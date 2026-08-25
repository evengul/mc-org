package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
import app.mcorg.pipeline.project.handleGetProject
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
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
import kotlin.test.assertIs

/**
 * MCO-294 — the plan suggests designs from the bank that cover its demand.
 *
 * The unit rules live in `FarmSuggestionsTest`; this covers the two things only the real door
 * can answer: that the suggestion reaches the rendered plan at all, and that it obeys the hub's
 * visibility rule rather than showing one user another's private designs.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class FarmSuggestionIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 94, 0)
    private val cobblestone = Item("minecraft:cobblestone", "Cobblestone")
    private val glassBottle = Item("minecraft:glass_bottle", "Glass Bottle")

    /** Farm-scale in the fixtures below, and produced by nothing in the bank. */
    private val oakLog = Item("minecraft:oak_log", "Oak Log")

    private var worldId: Int = 0
    private var projectId: Int = 0
    private var myFarmIdea: Int = 0
    private var strangersIdea: Int = 0
    private var smallIdea: Int = 0

    @BeforeAll
    fun setup() {
        val stored = runBlocking {
            StoreMinecraftDataStep.process(
                ServerData(
                    version = version,
                    items = listOf(cobblestone, glassBottle, oakLog),
                    sources = listOf(
                        ResourceSource(
                            type = ResourceSource.SourceType.LootTypes.BLOCK,
                            filename = "blocks/stone.json",
                            producedItems = listOf(cobblestone to ResourceQuantity.ItemQuantity(1))
                        ),
                        ResourceSource(
                            type = ResourceSource.SourceType.LootTypes.BLOCK,
                            filename = "blocks/glass_bottle.json",
                            producedItems = listOf(glassBottle to ResourceQuantity.ItemQuantity(1))
                        ),
                        ResourceSource(
                            type = ResourceSource.SourceType.LootTypes.BLOCK,
                            filename = "blocks/oak_log.json",
                            producedItems = listOf(oakLog to ResourceQuantity.ItemQuantity(1))
                        ),
                    )
                )
            )
        }
        assertIs<Result.Success<*>>(stored)

        worldId = createWorld("FarmSuggestion IT World")
        projectId = createProject(worldId, "Storage System")
        // Comfortably farm-scale (the default threshold is 1,728).
        createResourceGathering(projectId, cobblestone, required = 75_151)
        createResourceGathering(projectId, glassBottle, required = 216)

        myFarmIdea = createIdea("231k Cobblestone farm", ownerId = user.id, public = false)
        addProduction(myFarmIdea, cobblestone.id, 924_000)

        // Sub-threshold on its only output: the plan wants 216 bottles, which is no reason to
        // build anything.
        smallIdea = createIdea("Bottle trickle", ownerId = user.id, public = false)
        addProduction(smallIdea, glassBottle.id, 785)
    }

    @Test
    fun `a design covering farm-scale demand is suggested on the plan`() = testApplication {
        setupRoutes()

        val response = client.get("/worlds/$worldId/projects/$projectId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "plan-farm-scale")
        assertContains(body, "231k Cobblestone farm")
        assertContains(body, "75,151")
        // Since MCO-459 the row's action is a checkbox feeding the batch form, not a link
        // straight to the review. The review is still where it leads — one step later, and
        // for every design ticked at once (BatchImportWizardIT owns that flow).
        assertContains(body, "design-select-$myFarmIdea")
        assertContains(body, "/worlds/$worldId/projects/$projectId/farm-suggestions/import")
    }

    @Test
    fun `an answered line lives under its design, not in a second list`() = testApplication {
        setupRoutes()

        val body = client.get("/worlds/$worldId/projects/$projectId") { addAuthCookie(this) }.bodyAsText()

        // These were briefly two sections that reprinted each other's numbers on a real plan.
        // The quantity now belongs to the design that covers it, inside the one roll-up.
        assertContains(body, "plan-farm-scale__design")
        assertContains(body, "your designs cover 1 of them")
        assertFalse(body.contains("plan-suggestions"), "designs are not a section of their own")
        assertFalse(body.contains("No design yet"), "everything farm-scale here has a design")
    }

    @Test
    fun `farm-scale demand no design answers is called out as such`() = testApplication {
        setupRoutes()
        val bare = createProject(worldId, "Bare Build")
        createResourceGathering(bare, oakLog, required = 31_267)

        val body = client.get("/worlds/$worldId/projects/$bare") { addAuthCookie(this) }.bodyAsText()

        // Nothing in the bank makes oak logs. Not a gap in the feature — the honest answer that
        // this one gets farmed by hand, and where the bank is worth growing. With nothing
        // answered the section is exactly the roll-up MCO-401 shipped: quantities, no headings.
        assertContains(body, "31,267")
        assertContains(body, "Oak Log")
        assertFalse(body.contains("plan-farm-scale__design"), "there is no design to show")
        assertFalse(body.contains("No design yet"), "nothing to separate it from")
    }

    @Test
    fun `a project built from a design is not suggested that design`() = testApplication {
        setupRoutes()
        // The farm itself costs cobblestone to build, so its own plan matched its own design
        // and offered to import what you are standing in.
        val theFarm = createProjectFromIdea(worldId, "231k Cobblestone farm", myFarmIdea)
        createResourceGathering(theFarm, cobblestone, required = 4_000)

        val body = client.get("/worlds/$worldId/projects/$theFarm") { addAuthCookie(this) }.bodyAsText()

        assertFalse(
            body.contains("design-select-$myFarmIdea"),
            "you cannot need the design you are building",
        )
        // The demand is still farm-scale and still listed — it just has no answer now.
        assertContains(body, "4,000")
        assertFalse(body.contains("plan-farm-scale__design"), "and no design row at all")
    }

    @Test
    fun `a design whose only output is below the threshold is not suggested`() = testApplication {
        setupRoutes()

        val body = client.get("/worlds/$worldId/projects/$projectId") { addAuthCookie(this) }.bodyAsText()

        assertFalse(body.contains("Bottle trickle"), "216 bottles is not a reason to build a farm")
    }

    @Test
    fun `someone else's private design is never suggested`() = testApplication {
        setupRoutes()
        val stranger = createExtraUser("farm-suggestion-stranger")
        strangersIdea = createIdea("Stranger's Cobble Farm", ownerId = stranger.id, public = false)
        addProduction(strangersIdea, cobblestone.id, 500_000)

        val body = client.get("/worlds/$worldId/projects/$projectId") { addAuthCookie(this) }.bodyAsText()

        assertFalse(
            body.contains("Stranger's Cobble Farm"),
            "the bank is public designs plus your own — the hub's rule (MCO-291), shared not re-derived",
        )
    }

    @Test
    fun `published, the same design is suggested to everyone`() = testApplication {
        setupRoutes()
        val stranger = createExtraUser("farm-suggestion-publisher")
        val published = createIdea("Published Cobble Farm", ownerId = stranger.id, public = true)
        addProduction(published, cobblestone.id, 400_000)

        val body = client.get("/worlds/$worldId/projects/$projectId") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "Published Cobble Farm")

        deleteIdea(published)
    }

    @Test
    fun `a farm recorded by hand is not suggested a design for what it produces`() {
        // MCO-458's second door. `a project built from a design is not suggested that design`
        // covers the import door, which excludes by project_idea_id; a farm recorded through
        // MCO-298 has no such id, and used to be offered a design for its own output.
        val world = createWorld("Recorded farm world")
        val theFarm = createProject(world, "Hand-recorded Cobble Farm")
        addProjectProduction(theFarm, cobblestone, rate = 231_000)
        createResourceGathering(theFarm, cobblestone, required = 4_000)

        testApplication {
            setupRoutes()

            val body = client.get("/worlds/$world/projects/$theFarm") { addAuthCookie(this) }.bodyAsText()

            assertFalse(
                body.contains("231k Cobblestone farm"),
                "it produces cobblestone; a cobblestone design answers nothing it needs",
            )
            assertFalse(body.contains("plan-farm-scale__design"), "and so no design row at all")
            // The demand itself is untouched — still farm-scale, still listed, just unanswered.
            assertContains(body, "4,000")
        }
    }

    @Test
    fun `a farm already planned is not offered as an import`() {
        // MCO-461. The identical fixture to `a design covering farm-scale demand is suggested
        // on the plan` — which is the control showing the design *would* appear — plus a farm
        // the world has already filed. MCO-299's notice and the suggestion cannot both claim
        // the cobblestone.
        val world = createWorld("Planned farm world")
        val build = createProject(world, "Storage System")
        createResourceGathering(build, cobblestone, required = 75_151)

        val plannedFarm = createProject(world, "Filed Cobble Farm")
        addProjectProduction(plannedFarm, cobblestone, rate = 231_000)

        testApplication {
            setupRoutes()

            val body = client.get("/worlds/$world/projects/$build") { addAuthCookie(this) }.bodyAsText()

            assertContains(body, "plan-pending-farms")
            assertContains(body, "Filed Cobble Farm")
            assertFalse(
                body.contains("231k Cobblestone farm"),
                "the notice says it is coming; offering to import a second cobble farm contradicts it",
            )
            assertFalse(
                body.contains("plan-farm-scale__batch"),
                "with nothing to suggest there is no batch to open",
            )
        }
    }

    // ---- routing ----------------------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    get { call.handleGetProject() }
                }
            }
        }
    }

    // ---- fixtures ----------------------------------------------------------------

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = version)
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'PLANNING', ?, 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
                stmt.setString(3, ProjectState.ACTIVE.name)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createProjectFromIdea(worldId: Int, name: String, ideaId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension, project_idea_id) " +
                    "VALUES (?, ?, '', 'FARMING', 'PLANNING', 'ACTIVE', 0, 0, 0, 'OVERWORLD', ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
                stmt.setInt(3, ideaId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createResourceGathering(projectId: Int, item: Item, required: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, item.id)
                stmt.setString(3, item.name)
                stmt.setInt(4, required)
            }
        ).process(Unit)
    }

    private fun addProjectProduction(projectId: Int, item: Item, rate: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_productions (project_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, item.id)
                stmt.setString(3, item.name)
                stmt.setInt(4, rate)
            }
        ).process(Unit)
    }

    private fun createIdea(name: String, ownerId: Int, public: Boolean): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO ideas (name, description, category, author, difficulty, minecraft_version_range,
                                   category_data, created_by, visibility)
                VALUES (?, 'test idea', 'FARM', '{"type":"single","name":"tester"}'::jsonb, 'EASY',
                        '{"type":"app.mcorg.domain.model.minecraft.MinecraftVersionRange.Unbounded"}'::jsonb,
                        '{}'::jsonb, ?, ?)
                RETURNING id
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, ownerId)
                stmt.setString(3, if (public) "PUBLIC" else "PRIVATE")
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun addProduction(ideaId: Int, itemId: String, rate: Int) = runBlocking {
        val modeId = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO idea_production_modes (idea_id, name, position) VALUES (?, 'Default', 0) RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, ideaId) }
        ).process(Unit).let { (it as Result.Success).value }

        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO idea_production_rates (mode_id, item_id, rate_per_hour) VALUES (?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, modeId)
                stmt.setString(2, itemId)
                stmt.setInt(3, rate)
            }
        ).process(Unit)
    }

    private fun deleteIdea(ideaId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.delete("DELETE FROM ideas WHERE id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, ideaId) }
        ).process(Unit)
    }
}
