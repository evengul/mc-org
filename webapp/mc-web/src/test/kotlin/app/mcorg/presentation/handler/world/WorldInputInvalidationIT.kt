package app.mcorg.presentation.handler.world

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
import app.mcorg.pipeline.resources.DemandFingerprint
import app.mcorg.pipeline.resources.GetWorldDemandCoverageStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.roadmap.GetWorldRoadMapStep
import app.mcorg.pipeline.world.settings.general.UpdatePreferredWoodSpeciesStep
import app.mcorg.pipeline.world.settings.general.handleUpdatePreferredWoodSpecies
import app.mcorg.pipeline.world.settings.general.handleUpdateWorldVersion
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.WorldAdminPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.sql.Timestamp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Stored demand follows the inputs that belong to the whole world, not to one project (MCO-578).
 *
 * MCO-404 invalidated stored demand when a world's *supply* changed. The world's version and its
 * preferred wood species are inputs to every plan in it as well, and they were never wired up:
 * switching a world from 1.21.4 to 26.3.0 left the roadmap showing every plan derived against
 * 1.21.4 until each project page was opened. The same gap existed for a re-ingest of the same
 * version and for a change to the planner itself, neither of which the fingerprint could see.
 *
 * The version switch is driven end to end, through the roadmap's own fill-on-read path, on two
 * small game versions that build a furnace from different stone. The rest assert on
 * `project_demand_state`, as `DemandInvalidationIT` does: a missing or out-of-date state row is
 * exactly what makes that path re-derive.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class WorldInputInvalidationIT : WithUser() {

    /** Furnaces are made of cobblestone. */
    private val before = MinecraftVersion.Release(1, 93, 0)

    /** Furnaces are made of blackstone. */
    private val after = MinecraftVersion.Release(1, 93, 1)

    /** Same game data as [before]; its ledger row is what the re-ingest test moves. */
    private val reingested = MinecraftVersion.Release(1, 92, 0)

    private val furnace = Item("minecraft:furnace", "Furnace")
    private val cobblestone = Item("minecraft:cobblestone", "Cobblestone")
    private val blackstone = Item("minecraft:blackstone", "Blackstone")

    @BeforeAll
    fun setup() {
        store(before, furnaceFrom = cobblestone)
        store(after, furnaceFrom = blackstone)
        store(reingested, furnaceFrom = cobblestone)
        CacheManager.invalidateAll()
    }

    private fun store(version: MinecraftVersion.Release, furnaceFrom: Item) {
        val serverData = ServerData(
            version = version,
            items = listOf(furnace, cobblestone, blackstone),
            sources = listOf(
                block("blocks/cobblestone.json", cobblestone),
                block("blocks/blackstone.json", blackstone),
                ResourceSource(
                    type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                    filename = "furnace.json",
                    requiredItems = listOf(furnaceFrom to ResourceQuantity.ItemQuantity(8)),
                    producedItems = listOf(furnace to ResourceQuantity.ItemQuantity(1)),
                ),
            ),
        )
        assertIs<Result.Success<*>>(runBlocking { StoreMinecraftDataStep.process(serverData) })
    }

    private fun block(file: String, item: Item) = ResourceSource(
        type = ResourceSource.SourceType.LootTypes.BLOCK,
        filename = file,
        producedItems = listOf(item to ResourceQuantity.ItemQuantity(1)),
    )

    // ---- world version ----------------------------------------------------------------

    @Test
    fun `switching a world's version re-derives its plans against the new version on the next roadmap load`() =
        testApplication {
            setupRoutes()
            val worldId = createWorld("Version Switch Demand World", before)
            val smelter = createProject(worldId, "Smelter")
            gather(smelter, furnace, 1)
            loadRoadmap(worldId)
            assertTrue(cobblestone.id in demandItems(smelter), "the 1.93.0 plan builds furnaces from cobblestone")

            val response = client.patch("/worlds/$worldId/settings/version") {
                addAuthCookie(this)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("version=$after")
            }
            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            assertFalse(hasState(smelter), "a version switch drops the fingerprint of every plan in the world")

            loadRoadmap(worldId)
            val items = demandItems(smelter)
            assertTrue(blackstone.id in items, "the roadmap re-derived against 1.93.1: $items")
            assertFalse(cobblestone.id in items, "nothing from the 1.93.0 plan is left: $items")
        }

    @Test
    fun `switching a world's version leaves other worlds' plans alone`() = testApplication {
        setupRoutes()
        val switched = createWorld("Version Switch World A", before)
        val untouched = createWorld("Version Switch World B", before)
        val inSwitched = listOf(createProject(switched, "First"), createProject(switched, "Second"))
        val inUntouched = createProject(untouched, "Elsewhere")
        (inSwitched + inUntouched).forEach { stampState(it) }

        val response = client.patch("/worlds/$switched/settings/version") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("version=$after")
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        inSwitched.forEach { assertFalse(hasState(it), "every plan in the switched world is stale") }
        assertTrue(hasState(inUntouched), "another world's version did not change")
    }

    @Test
    fun `re-saving the version a world already has invalidates nothing`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Version Same World", before)
        val projectId = createProject(worldId, "Smelter")
        stampState(projectId)

        val response = client.patch("/worlds/$worldId/settings/version") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("version=$before")
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertTrue(hasState(projectId), "nothing a plan reads has changed, so nothing should re-derive")
    }

    // ---- preferred wood species ---------------------------------------------------------

    @Test
    fun `changing the preferred wood species drops every plan in the world, and only in that world`() =
        testApplication {
            setupRoutes()
            val changed = createWorld("Wood Change World A", before)
            val untouched = createWorld("Wood Change World B", before)
            val inChanged = listOf(createProject(changed, "Cabin"), createProject(changed, "Dock"))
            val inUntouched = createProject(untouched, "Elsewhere")
            (inChanged + inUntouched).forEach { stampState(it) }

            val response = client.patch("/worlds/$changed/settings/preferred-wood-species") {
                addAuthCookie(this)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("preferredWoodSpecies=spruce")
            }

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            inChanged.forEach { assertFalse(hasState(it), "every wood tag in every plan resolves differently now") }
            assertTrue(hasState(inUntouched), "another world's wood did not change")
        }

    @Test
    fun `clearing the preferred wood species is a change too`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Wood Clear World", before)
        runBlocking { UpdatePreferredWoodSpeciesStep(worldId).process("oak") }
        val projectId = createProject(worldId, "Cabin")
        stampState(projectId)

        val response = client.patch("/worlds/$worldId/settings/preferred-wood-species") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("preferredWoodSpecies=")
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertFalse(hasState(projectId), "the wood tags go back to being asked, which is a different plan")
    }

    @Test
    fun `re-saving the wood species a world already has invalidates nothing`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Wood Same World", before)
        runBlocking { UpdatePreferredWoodSpeciesStep(worldId).process("birch") }
        val projectId = createProject(worldId, "Cabin")
        stampState(projectId)

        val response = client.patch("/worlds/$worldId/settings/preferred-wood-species") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("preferredWoodSpecies=birch")
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertTrue(hasState(projectId))
    }

    /**
     * The drill's "and use this wood everywhere" box (MCO-487) is the second door to the same
     * column, and it writes through the step rather than the settings handler.
     */
    @Test
    fun `the drill's use-this-wood-everywhere write invalidates as well`() {
        val worldId = createWorld("Wood Drill World", before)
        val projectId = createProject(worldId, "Cabin")
        stampState(projectId)

        runBlocking { UpdatePreferredWoodSpeciesStep(worldId).process("cherry") }

        assertFalse(hasState(projectId))
    }

    // ---- revisions and re-ingests -------------------------------------------------------

    @Test
    fun `a plan derived by an earlier revision of the planner counts as not yet derived`() {
        val worldId = createWorld("Revision World", before)
        val current = createProject(worldId, "Current")
        val outdated = createProject(worldId, "Outdated")
        listOf(current, outdated).forEach { gather(it, furnace, 1) }
        stampState(current, revision = DemandFingerprint.REVISION)
        stampState(outdated, revision = DemandFingerprint.REVISION - 1)

        assertEquals(listOf(outdated), uncovered(worldId))
    }

    @Test
    fun `a re-ingest of the world's version re-derives its plans once, on the next roadmap load`() {
        val worldId = createWorld("Reingest World", reingested)
        val projectId = createProject(worldId, "Smelter")
        gather(projectId, furnace, 1)
        loadRoadmap(worldId)
        assertEquals(emptyList(), uncovered(worldId))
        val firstDerivation = derivedAt(projectId)

        markIngested(reingested)
        assertEquals(listOf(projectId), uncovered(worldId), "the game data under the stored plan changed")

        loadRoadmap(worldId)
        val secondDerivation = derivedAt(projectId)
        assertTrue(secondDerivation.after(firstDerivation), "the roadmap re-derived it")
        assertEquals(
            emptyList(), uncovered(worldId),
            "and the rewrite counts as current: an unchanged fingerprint must not skip it, or every " +
                "roadmap load would re-derive this project forever",
        )

        loadRoadmap(worldId)
        assertEquals(secondDerivation, derivedAt(projectId), "a third load has nothing to do")
    }

    /**
     * The epoch is cached for a minute, and the graph's staleness is judged against that cache. A
     * derivation that read the cached epoch after a second re-ingest stored the old epoch's
     * fingerprint, found it equal to what was stored, and skipped the write — so for that minute
     * every roadmap load re-derived every project in the world and kept none of it. With
     * `derived_at` as the yardstick it could also store a plan of the old graph as current.
     */
    @Test
    fun `a re-ingest is seen at once, not when the cached epoch expires`() {
        val worldId = createWorld("Reingest Twice World", reingested)
        val projectId = createProject(worldId, "Smelter")
        gather(projectId, furnace, 1)
        markIngested(reingested)
        loadRoadmap(worldId)
        assertEquals(emptyList(), uncovered(worldId))

        markIngested(reingested, clearCaches = false)
        loadRoadmap(worldId)

        assertEquals(emptyList(), uncovered(worldId), "re-derived against the new epoch, and kept")
        assertEquals(ledgerEpoch(reingested), storedEpoch(projectId))
    }

    // ---- routing — mirrors WorldHandler -------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                route("/settings") {
                    install(WorldAdminPlugin)
                    patch("/version") { call.handleUpdateWorldVersion() }
                    patch("/preferred-wood-species") { call.handleUpdatePreferredWoodSpecies() }
                }
            }
        }
        CacheManager.supportedVersions.invalidateAll()
    }

    // ---- fixtures -----------------------------------------------------------------------

    private fun createWorld(name: String, version: MinecraftVersion): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = version)
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'PLANNING', 'ACTIVE', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun gather(projectId: Int, item: Item, required: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, item.id)
                stmt.setString(3, item.name)
                stmt.setInt(4, required)
            },
        ).process(Unit)
        Unit
    }

    private fun loadRoadmap(worldId: Int) {
        assertIs<Result.Success<*>>(runBlocking { GetWorldRoadMapStep(worldId).process(Unit) })
    }

    /** Moves the version's ingestion epoch to now, as a completed re-ingest does. */
    private fun markIngested(version: MinecraftVersion, clearCaches: Boolean = true) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO minecraft_version_ingestion (version, status, completed_at)
                VALUES (?, 'completed', clock_timestamp())
                ON CONFLICT (version) DO UPDATE SET status = 'completed', completed_at = EXCLUDED.completed_at
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ -> stmt.setString(1, version.toString()) },
        ).process(Unit)
        // The epoch is cached with a short TTL. Clearing it stands for "after the TTL"; not
        // clearing it is the minute in between.
        if (clearCaches) CacheManager.invalidateAll()
    }

    private fun ledgerEpoch(version: MinecraftVersion): Timestamp = runBlocking {
        val result = DatabaseSteps.query<Unit, Timestamp>(
            sql = SafeSQL.select("SELECT completed_at FROM minecraft_version_ingestion WHERE version = ?"),
            parameterSetter = { stmt, _ -> stmt.setString(1, version.toString()) },
            resultMapper = { rs -> rs.next(); rs.getTimestamp("completed_at") }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun storedEpoch(projectId: Int): Timestamp? = runBlocking {
        val result = DatabaseSteps.query<Unit, Timestamp?>(
            sql = SafeSQL.select("SELECT game_data_epoch FROM project_demand_state WHERE project_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> if (rs.next()) rs.getTimestamp("game_data_epoch") else null }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun stampState(projectId: Int, revision: Int = DemandFingerprint.REVISION) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_demand_state (project_id, fingerprint, derived_at, revision) " +
                    "VALUES (?, 'fp-test', now(), ?) " +
                    "ON CONFLICT (project_id) DO UPDATE SET fingerprint = EXCLUDED.fingerprint, revision = EXCLUDED.revision"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setInt(2, revision)
            }
        ).process(Unit)
    }

    private fun hasState(projectId: Int): Boolean = runBlocking {
        val result = DatabaseSteps.query<Unit, Boolean>(
            sql = SafeSQL.select("SELECT 1 FROM project_demand_state WHERE project_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next() }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun derivedAt(projectId: Int): Timestamp = runBlocking {
        val result = DatabaseSteps.query<Unit, Timestamp>(
            sql = SafeSQL.select("SELECT derived_at FROM project_demand_state WHERE project_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next(); rs.getTimestamp("derived_at") }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun demandItems(projectId: Int): Set<String> = runBlocking {
        val result = DatabaseSteps.query<Unit, Set<String>>(
            sql = SafeSQL.select("SELECT item_id FROM project_demand WHERE project_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> buildSet { while (rs.next()) add(rs.getString("item_id")) } }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun uncovered(worldId: Int): List<Int> = runBlocking {
        (GetWorldDemandCoverageStep(worldId).process(Unit) as Result.Success).value
    }
}
