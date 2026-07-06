package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleGetDetailContent
import app.mcorg.pipeline.resources.handleGetResourceDetailPanel
import app.mcorg.pipeline.resources.handleSwapResourceGatheringVariant
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.ResourceGatheringIdParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Integration tests for MCO-246: PATCH /resources/gathering/{id}/variant.
 *
 * The Testcontainers DB carries no ingested Minecraft data (see [PlanChainIT]'s note), so this
 * class seeds a small, self-contained fake "Minecraft version" graph directly into the
 * `minecraft_tag`/`minecraft_tag_item`/`resource_source*` tables — the same tables real
 * ingestion writes to (see `StoreServerDataSteps.kt`) — giving [GetItemSourceGraphForVersionStep]
 * real tag/member and recipe data to build a graph from, without depending on a real ingest.
 *
 * The fake graph models:
 * - a "planks" tag (birch/spruce) usable for sticks, birch planks crafted from an oak log,
 *   spruce planks crafted from a spruce log — so a tag-family swap (birch -> spruce) re-routes
 *   the derived "How to make it" breakdown through a different raw material (oak log -> spruce
 *   log), and the tag siblings surface as "Suggested" quick-swap chips.
 * - stone (raw gather) and white_concrete (crafted from white_concrete_powder) sharing NO tag —
 *   so an any-item swap (stone -> concrete), the MCO-246 pivot, also succeeds and re-derives.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class SwapResourceGatheringVariantIT : WithUser() {

    // Unique per test class so CacheManager's in-memory graph cache (keyed by version string)
    // never collides with another test class's version, even if surefire reuses the JVM fork.
    private val fakeVersion = "246.0.0"

    private var worldId: Int = 0

    @BeforeAll
    fun setup() {
        runBlocking { seedFakeGraph(fakeVersion) }
        worldId = createWorld(fakeVersion)
    }

    // -------------------------------------------------------------------------
    // Success: swap changes the row + re-derives the plan with new dependencies
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH variant swaps the item id and name and persists it`() = testApplication {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:birch_planks", "Birch Planks", 8)
        setupRoutes()

        val response = client.patch(
            "/worlds/$worldId/projects/$pid/resources/gathering/$rgId/variant"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=minecraft:spruce_planks")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "id=\"plan-resources-area\"")
        // The row's own item cell now reads the new name. (The OOB variant panel legitimately
        // still mentions "Birch Planks" as the swap-back candidate, so we assert on the row
        // cell specifically rather than the whole body's absence of the old name.)
        assertContains(body, "<td class=\"plan-resource-table__item\">Spruce Planks</td>")
        assertFalse(body.contains("<td class=\"plan-resource-table__item\">Birch Planks</td>"))

        val (itemId, name) = runBlocking { readResource(rgId) }
        assertEquals("minecraft:spruce_planks", itemId)
        assertEquals("Spruce Planks", name)
    }

    @Test
    fun `swap to an item in no shared tag family (stone to concrete) succeeds and re-derives`() = testApplication {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:stone", "Stone", 4)
        setupRoutes()

        // Before: Stone is a raw-gather block — the breakdown has no White Concrete Powder.
        val before = client.get("/worlds/$worldId/projects/$pid/detail-content?lens=list") {
            addAuthCookie(this)
        }.bodyAsText()
        assertContains(before, "Stone")
        assertFalse(before.contains("White Concrete Powder"))

        // Stone and White Concrete share NO tag, so this is the any-item path (not a suggestion).
        val response = client.patch("/worlds/$worldId/projects/$pid/resources/gathering/$rgId/variant") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=minecraft:white_concrete")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val (itemId, name) = runBlocking { readResource(rgId) }
        assertEquals("minecraft:white_concrete", itemId)
        assertEquals("White Concrete", name)

        // After: the breakdown re-derives White Concrete through its White Concrete Powder input.
        val after = client.get("/worlds/$worldId/projects/$pid/detail-content?lens=list") {
            addAuthCookie(this)
        }.bodyAsText()
        assertContains(after, "White Concrete Powder")
    }

    @Test
    fun `swap re-derives the plan through the new item's own dependencies`() = testApplication {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:birch_planks", "Birch Planks", 8)
        setupRoutes()

        // Before the swap: the breakdown derives birch planks from an oak log.
        val before = client.get("/worlds/$worldId/projects/$pid/detail-content?lens=list") {
            addAuthCookie(this)
        }.bodyAsText()
        assertContains(before, "Oak Log")
        assertFalse(before.contains("Spruce Log"))

        client.patch("/worlds/$worldId/projects/$pid/resources/gathering/$rgId/variant") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=minecraft:spruce_planks")
        }

        // After the swap: the breakdown derives spruce planks from a spruce log instead.
        val after = client.get("/worlds/$worldId/projects/$pid/detail-content?lens=list") {
            addAuthCookie(this)
        }.bodyAsText()
        assertContains(after, "Spruce Log")
        assertFalse(after.contains("Oak Log"))
    }

    @Test
    fun `GET detail-panel shows tag-family suggestions and the free-text search combo`() = testApplication {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:birch_planks", "Birch Planks", 8)
        setupRoutes()

        val response = client.get(
            "/worlds/$worldId/projects/$pid/resources/gathering/$rgId/detail-panel"
        ) { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // Tag-family sibling surfaces as a "Suggested" quick-swap chip.
        assertContains(body, "Suggested")
        assertContains(body, "Spruce Planks")
        // The free-text search combo is present for any-item swaps (reuses /items/search).
        assertContains(body, "resource-panel-variant-results")
        assertContains(body, "/items/search")
    }

    // -------------------------------------------------------------------------
    // Validation failure: swapping to an unknown item is rejected
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH variant with an unknown item id returns 422`() = testApplication {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:birch_planks", "Birch Planks", 8)
        setupRoutes()

        val response = client.patch(
            "/worlds/$worldId/projects/$pid/resources/gathering/$rgId/variant"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            // Not present in the version catalog — rejected even though it looks like a real id.
            setBody("itemId=minecraft:does_not_exist")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val (itemId, _) = runBlocking { readResource(rgId) }
        assertEquals("minecraft:birch_planks", itemId, "item must not change when validation fails")
    }

    @Test
    fun `PATCH variant with a missing itemId returns 400`() = testApplication {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:birch_planks", "Birch Planks", 8)
        setupRoutes()

        val response = client.patch(
            "/worlds/$worldId/projects/$pid/resources/gathering/$rgId/variant"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("")
        }

        // A missing (not merely invalid) parameter is a MissingParameter failure -> 400,
        // distinct from the 422 an out-of-family itemId gets (see the test above).
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH variant without auth returns redirect`() = testApplication {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:birch_planks", "Birch Planks", 8)
        setupRoutes()

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.patch(
            "/worlds/$worldId/projects/$pid/resources/gathering/$rgId/variant"
        ) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=minecraft:spruce_planks")
        }

        assertEquals(HttpStatusCode.Found, response.status)
    }

    @Test
    fun `PATCH variant by a non-member of the world returns 403`() = testApplication {
        val pid = createProject(worldId)
        val rgId = createResourceGathering(pid, "minecraft:birch_planks", "Birch Planks", 8)
        setupRoutes()

        val nonMember = createExtraUser()
        val response = client.patch(
            "/worlds/$worldId/projects/$pid/resources/gathering/$rgId/variant"
        ) {
            addAuthCookie(this, nonMember)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=minecraft:spruce_planks")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // -------------------------------------------------------------------------
    // Routing setup
    // -------------------------------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    get("/detail-content") { call.handleGetDetailContent() }
                    route("/resources/gathering/{resourceGatheringId}") {
                        install(ResourceGatheringIdParamPlugin)
                        get("/detail-panel") { call.handleGetResourceDetailPanel() }
                        patch("/variant") { call.handleSwapResourceGatheringVariant() }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // DB fixture helpers
    // -------------------------------------------------------------------------

    private fun createWorld(version: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "SwapVariant IT World",
                description = "test",
                version = MinecraftVersion.fromString(version)
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                        "VALUES ('SwapVariant IT Project', ?, '', 'BUILDING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createResourceGathering(projectId: Int, itemId: String, name: String, required: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
                stmt.setInt(4, required)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private suspend fun readResource(rgId: Int): Pair<String, String> {
        val result = DatabaseSteps.query<Int, Pair<String, String>>(
            sql = SafeSQL.select("SELECT item_id, name FROM resource_gathering WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs ->
                rs.next()
                rs.getString("item_id") to rs.getString("name")
            }
        ).process(rgId)
        return (result as Result.Success).value
    }

    private suspend fun item(version: String, itemId: String, name: String) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO minecraft_items (version, item_id, item_name) VALUES (?, ?, ?) " +
                        "ON CONFLICT (version, item_id) DO NOTHING"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
            }
        ).process(Unit)
    }

    private suspend fun tag(version: String, tag: String, name: String) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO minecraft_tag (version, tag, name) VALUES (?, ?, ?) ON CONFLICT (version, tag) DO NOTHING"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setString(2, tag)
                stmt.setString(3, name)
            }
        ).process(Unit)
    }

    private suspend fun tagItem(version: String, tag: String, itemId: String) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO minecraft_tag_item (version, tag, item) VALUES (?, ?, ?) " +
                        "ON CONFLICT (version, tag, item) DO NOTHING"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setString(2, tag)
                stmt.setString(3, itemId)
            }
        ).process(Unit)
    }

    /** Inserts a `resource_source` row and returns its generated id. */
    private suspend fun source(version: String, sourceType: String, filename: String): Int {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_source (version, source_type, created_from_filename) VALUES (?, ?, ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setString(2, sourceType)
                stmt.setString(3, filename)
            }
        ).process(Unit)
        return (result as Result.Success).value
    }

    private suspend fun consumedItem(version: String, sourceId: Int, itemId: String, count: Int) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_source_consumed_item (version, resource_source_id, item, count) VALUES (?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setInt(2, sourceId)
                stmt.setString(3, itemId)
                stmt.setInt(4, count)
            }
        ).process(Unit)
    }

    private suspend fun consumedTag(version: String, sourceId: Int, tag: String, count: Int) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_source_consumed_tag (version, resource_source_id, tag, count) VALUES (?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setInt(2, sourceId)
                stmt.setString(3, tag)
                stmt.setInt(4, count)
            }
        ).process(Unit)
    }

    private suspend fun producedItem(version: String, sourceId: Int, itemId: String, count: Int) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_source_produced_item (version, resource_source_id, item, count) VALUES (?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setInt(2, sourceId)
                stmt.setString(3, itemId)
                stmt.setInt(4, count)
            }
        ).process(Unit)
    }

    /**
     * Seeds a small fake graph for [version] directly into the ingestion tables:
     * - `#test:planks` tag containing birch_planks + spruce_planks (used by a "sticks from any
     *   planks" recipe, so the tag becomes a graph node — see [findVariantCandidates]'s doc).
     * - birch_planks crafted from an oak log; spruce_planks crafted from a spruce log.
     * - oak_log / spruce_log are raw-gather terminals (a source with no inputs).
     */
    private suspend fun seedFakeGraph(version: String) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert("INSERT INTO minecraft_version (version) VALUES (?) ON CONFLICT (version) DO NOTHING"),
            parameterSetter = { stmt, _ -> stmt.setString(1, version) }
        ).process(Unit)

        item(version, "minecraft:oak_log", "Oak Log")
        item(version, "minecraft:spruce_log", "Spruce Log")
        item(version, "minecraft:birch_planks", "Birch Planks")
        item(version, "minecraft:spruce_planks", "Spruce Planks")
        item(version, "minecraft:stick", "Stick")

        tag(version, "#test:planks", "Planks")
        tagItem(version, "#test:planks", "minecraft:birch_planks")
        tagItem(version, "#test:planks", "minecraft:spruce_planks")

        val stickSource = source(version, "minecraft:crafting_shapeless", "stick_from_planks.json")
        consumedTag(version, stickSource, "#test:planks", 2)
        producedItem(version, stickSource, "minecraft:stick", 4)

        val oakLogSource = source(version, "minecraft:block", "blocks/oak_log.json")
        producedItem(version, oakLogSource, "minecraft:oak_log", 1)

        val spruceLogSource = source(version, "minecraft:block", "blocks/spruce_log.json")
        producedItem(version, spruceLogSource, "minecraft:spruce_log", 1)

        val birchPlanksSource = source(version, "minecraft:crafting_shapeless", "birch_planks_from_oak_log.json")
        consumedItem(version, birchPlanksSource, "minecraft:oak_log", 1)
        producedItem(version, birchPlanksSource, "minecraft:birch_planks", 4)

        val sprucePlanksSource = source(version, "minecraft:crafting_shapeless", "spruce_planks_from_spruce_log.json")
        consumedItem(version, sprucePlanksSource, "minecraft:spruce_log", 1)
        producedItem(version, sprucePlanksSource, "minecraft:spruce_planks", 4)

        // Stone and White Concrete share NO tag — the any-item swap (MCO-246 pivot). Stone is a
        // raw-gather block; white concrete is crafted from white concrete powder (also raw), so a
        // stone -> white_concrete swap re-derives the breakdown to a new dependency.
        item(version, "minecraft:stone", "Stone")
        item(version, "minecraft:white_concrete", "White Concrete")
        item(version, "minecraft:white_concrete_powder", "White Concrete Powder")

        val stoneSource = source(version, "minecraft:block", "blocks/stone.json")
        producedItem(version, stoneSource, "minecraft:stone", 1)

        val powderSource = source(version, "minecraft:block", "blocks/white_concrete_powder.json")
        producedItem(version, powderSource, "minecraft:white_concrete_powder", 1)

        val concreteSource = source(version, "minecraft:crafting_shapeless", "white_concrete_from_powder.json")
        consumedItem(version, concreteSource, "minecraft:white_concrete_powder", 1)
        producedItem(version, concreteSource, "minecraft:white_concrete", 1)
    }
}
