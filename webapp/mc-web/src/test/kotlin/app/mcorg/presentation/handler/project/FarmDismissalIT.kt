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
import app.mcorg.pipeline.resources.handleDismissFarmSuggestion
import app.mcorg.pipeline.resources.handleRestoreFarmSuggestion
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldAdminPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
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
 * MCO-407 — a world can dismiss a farm-scale suggestion it has decided against.
 *
 * The rules live in `FarmScaleDemandsTest`; what only the real door can answer is that the
 * decision is written, survives a reload, clears the row badge as well as the roll-up line,
 * can be taken back, and is not something any member can make for everybody.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class FarmDismissalIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 95, 0)
    private val cobblestone = Item("minecraft:cobblestone", "Cobblestone")
    private val ice = Item("minecraft:ice", "Ice")

    private var worldId: Int = 0
    private var projectId: Int = 0

    @BeforeAll
    fun setup() {
        val stored = runBlocking {
            StoreMinecraftDataStep.process(
                ServerData(
                    version = version,
                    items = listOf(cobblestone, ice),
                    sources = listOf(
                        ResourceSource(
                            type = ResourceSource.SourceType.LootTypes.BLOCK,
                            filename = "blocks/stone.json",
                            producedItems = listOf(cobblestone to ResourceQuantity.ItemQuantity(1))
                        ),
                        ResourceSource(
                            type = ResourceSource.SourceType.LootTypes.BLOCK,
                            filename = "blocks/ice.json",
                            producedItems = listOf(ice to ResourceQuantity.ItemQuantity(1))
                        ),
                    )
                )
            )
        }
        assertIs<Result.Success<*>>(stored)

        worldId = createWorld("Farm dismissal IT world")
        projectId = createProject(worldId, "Ice Road")
        createResourceGathering(projectId, cobblestone, required = 74_557)
        createResourceGathering(projectId, ice, required = 20_611)
    }

    @Test
    fun `a dismissed item stays dismissed across a reload`() = testApplication {
        setupRoutes()
        // Its own world per test: a dismissal is world-scoped by design, so sharing one world
        // would make these tests depend on the order they happen to run in.
        val world = createWorld("Reload world")
        val project = createProject(world, "Reload build")
        createResourceGathering(project, cobblestone, required = 74_557)
        createResourceGathering(project, ice, required = 20_611)

        assertContains(planIn(world, project), "2 raw materials need more than")

        val dismissed = client.post(dismissalUrl(project, ice.id, world)) { addAuthCookie(this) }
        assertEquals(HttpStatusCode.OK, dismissed.status)
        // The response is the plan itself, not a receipt — the roll-up has already lost the line.
        assertContains(dismissed.bodyAsText(), "1 raw material needs more than")

        // The reload is the point: a decision that only lived in the swapped fragment would
        // pass the assertion above and still fail the user.
        val after = planIn(world, project)
        assertContains(after, "1 raw material needs more than", message = "the ice line is gone on a fresh load")
        assertContains(after, "74,557", message = "and the cobblestone line is untouched")
        assertContains(after, "1 dismissed", message = "with the undo where it can be found")
        assertContains(after, "20,611 here", message = "and today's demand beside the decision")
    }

    @Test
    fun `a dismissed item carries no row badge`() = testApplication {
        setupRoutes()
        val world = createWorld("Badge world")
        val project = createProject(world, "Badge build")
        createResourceGathering(project, ice, required = 20_611)

        assertContains(planIn(world, project), "More than this world's farm-scale threshold")

        client.post(dismissalUrl(project, ice.id, world)) { addAuthCookie(this) }

        val after = planIn(world, project)
        assertFalse(
            after.contains("More than this world's farm-scale threshold"),
            "the badge is the same rule as the roll-up, so it goes with it",
        )
        assertContains(after, "Nothing in this plan is above", message = "and the roll-up says so")
        assertContains(after, "20,611", message = "the work itself is untouched — you still need the ice")
    }

    @Test
    fun `a dismissal is reversible`() = testApplication {
        setupRoutes()
        val world = createWorld("Undo world")
        val project = createProject(world, "Undo build")
        createResourceGathering(project, ice, required = 20_611)

        client.post(dismissalUrl(project, ice.id, world)) { addAuthCookie(this) }
        assertContains(planIn(world, project), "Nothing in this plan is above")

        val restored = client.delete(dismissalUrl(project, ice.id, world)) { addAuthCookie(this) }
        assertEquals(HttpStatusCode.OK, restored.status)

        val after = planIn(world, project)
        assertContains(after, "1 raw material needs more than", message = "restoring puts the line back")
        assertFalse(after.contains("1 dismissed"), "and the fold has nothing left to hold")
    }

    @Test
    fun `a dismissal survives the threshold changing`() = testApplication {
        setupRoutes()
        val world = createWorld("Threshold-change world")
        val project = createProject(world, "Threshold build")
        createResourceGathering(project, ice, required = 20_611)

        client.post(dismissalUrl(project, ice.id, world)) { addAuthCookie(this) }

        // Both directions. Raising the threshold past the demand is the blunt instrument this
        // replaces; lowering it is where a demand-derived revival rule would fire.
        setThreshold(world, 50_000)
        assertContains(planIn(world, project), "Nothing in this plan is above")

        setThreshold(world, 100)
        val after = planIn(world, project)
        assertContains(
            after,
            "Nothing in this plan is above",
            message = "a dismissal a threshold change undoes is only a slower way of raising it",
        )
        assertContains(after, "1 dismissed")
    }

    @Test
    fun `it applies to every project in the world, and only that world`() = testApplication {
        setupRoutes()
        val world = createWorld("Scoped world")
        val first = createProject(world, "First build")
        createResourceGathering(first, ice, required = 20_611)
        val second = createProject(world, "Second build")
        createResourceGathering(second, ice, required = 20_611)

        val otherWorld = createWorld("Untouched world")
        val elsewhere = createProject(otherWorld, "Elsewhere build")
        createResourceGathering(elsewhere, ice, required = 20_611)

        client.post(dismissalUrl(first, ice.id, world)) { addAuthCookie(this) }

        assertContains(
            planIn(world, second),
            "Nothing in this plan is above",
            message = "world-scoped: 'I do not farm ice' is not a statement about one build",
        )
        assertContains(
            planIn(otherWorld, elsewhere),
            "1 raw material needs more than",
            message = "and it says nothing about a different world",
        )
    }

    @Test
    fun `a member who is not an admin cannot dismiss for everybody`() = testApplication {
        setupRoutes()
        val stranger = createExtraUser("farm-dismissal-stranger")

        val response = client.post(dismissalUrl(projectId, cobblestone.id)) { addAuthCookie(this, stranger) }

        assertEquals(
            HttpStatusCode.Forbidden,
            response.status,
            "dismissal overrides a world setting; the route is gated like the setting is",
        )
        assertContains(plan(projectId), "74,557", message = "and nothing was written")
    }

    // ---- helpers ----------------------------------------------------------------

    private fun dismissalUrl(project: Int, itemId: String, world: Int = worldId): String =
        "/worlds/$world/projects/$project/farm-suggestions/dismissals/" +
            itemId.replace(":", "%3A")

    private suspend fun ApplicationTestBuilder.plan(project: Int): String = planIn(worldId, project)

    private suspend fun ApplicationTestBuilder.planIn(world: Int, project: Int): String =
        client.get("/worlds/$world/projects/$project") { addAuthCookie(this) }.bodyAsText()

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
                    route("/farm-suggestions/dismissals/{itemId}") {
                        install(WorldAdminPlugin)
                        post { call.handleDismissFarmSuggestion() }
                        delete { call.handleRestoreFarmSuggestion() }
                    }
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

    private fun setThreshold(worldId: Int, threshold: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE world SET farm_scale_threshold = ? WHERE id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, threshold)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
    }
}
