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
import app.mcorg.pipeline.project.handleGetProjectList
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
 * How farm supply reads in the planner and the Field Log (MCO-299):
 * - an operational farm's items sit in "Collect from farms" badged as a Farm
 * - a farm that is not running yet produces the partial-dependency notice instead
 * - a producing farm is not shelved with finished builds in the Field Log
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class FarmSupplySurfacingIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 97, 0)
    private val ironIngot = Item("minecraft:iron_ingot", "Iron Ingot")

    private var worldId: Int = 0
    private var consumerId: Int = 0
    private var farmId: Int = 0

    @BeforeAll
    fun setup() {
        val stored = runBlocking {
            StoreMinecraftDataStep.process(
                ServerData(
                    version = version,
                    items = listOf(ironIngot),
                    sources = listOf(
                        ResourceSource(
                            type = ResourceSource.SourceType.LootTypes.BLOCK,
                            filename = "blocks/iron_ore.json",
                            producedItems = listOf(ironIngot to ResourceQuantity.ItemQuantity(1))
                        )
                    )
                )
            )
        }
        assertIs<Result.Success<*>>(stored)

        worldId = createWorld("FarmSupplySurfacing IT World")
        consumerId = createProject(worldId, "Beacon Build", ProjectState.PENDING)
        farmId = createProject(worldId, "Iron Farm", ProjectState.ACTIVE)
        createResourceGathering(consumerId, ironIngot, required = 32)
        insertProduction(farmId, ironIngot, rate = 400)
    }

    @Test
    fun `a farm that is not running yet reads as a prerequisite, not a supplied row`() = testApplication {
        setupRoutes()
        setProjectState(farmId, ProjectState.ACTIVE)

        val body = client.get("/worlds/$worldId/projects/$consumerId") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "plan-pending-farms")
        assertContains(body, "Iron Farm")
        // MCO-461 turned MCO-299's promise into an ordering fact. The promise is still here as
        // the explanation, but the relationship leads — a *notice* is what let MCO-294 offer to
        // import this same farm again right beside it.
        assertContains(body, "Prerequisites")
        assertContains(body, "comes first")
        assertContains(body, "32 Iron Ingot")
        assertContains(body, "by hand until it is running")
        assertFalse(body.contains("Collect from farms"), "the item is still manual work")
    }

    @Test
    fun `once the farm is done the item moves to collect-from-farms and the notice disappears`() = testApplication {
        setupRoutes()
        setProjectState(farmId, ProjectState.DONE)

        val body = client.get("/worlds/$worldId/projects/$consumerId") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "Collect from farms")
        assertContains(body, "from Iron Farm")
        assertFalse(body.contains("plan-pending-farms"), "nothing is pending once the farm runs")

        setProjectState(farmId, ProjectState.ACTIVE)
    }

    @Test
    fun `a supplied row prints the demand but no counter`() = testApplication {
        setupRoutes()
        setProjectState(farmId, ProjectState.DONE)

        val body = client.get("/worlds/$worldId/projects/$consumerId") { addAuthCookie(this) }.bodyAsText()

        // MCO-403: the row used to be name + badge + "from Iron Farm", which reads as solved.
        // The quantity is the fact that says whether the farm is anywhere near adequate, and it
        // is the same number the roadmap prints on that farm's edge.
        assertContains(body, """<span class="resource-row__count">32</span>""")
        assertFalse(
            body.contains("plan-count-minecraft-iron_ingot"),
            "supplied work is not scheduled by the plan, so it gets no collected/required counter",
        )

        setProjectState(farmId, ProjectState.ACTIVE)
    }

    @Test
    fun `a producing farm is listed as producing, not shelved with finished builds`() = testApplication {
        setupRoutes()
        setProjectState(farmId, ProjectState.DONE)

        val response = client.get("/worlds/$worldId/projects") { addAuthCookie(this) }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()

        assertContains(body, "fl-producing-section")
        assertContains(body, "Producing · 1")
        assertFalse(body.contains("1 done"), "a running farm is not shelf material")

        setProjectState(farmId, ProjectState.ACTIVE)
    }

    @Test
    fun `a finished build with no productions still goes to the shelf`() = testApplication {
        setupRoutes()
        val inertBuild = createProject(worldId, "Finished Statue", ProjectState.DONE)

        val body = client.get("/worlds/$worldId/projects") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "1 done")
        assertFalse(body.contains("fl-producing-section"), "no farm is producing in this state")

        deleteProject(inertBuild)
    }

    // ---- routing ----------------------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get { call.handleGetProjectList() }
                    route("/{projectId}") {
                        install(ProjectParamPlugin)
                        get { call.handleGetProject() }
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

    private fun createProject(worldId: Int, name: String, state: ProjectState): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'FARMING', 'PLANNING', ?, 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
                stmt.setString(3, state.name)
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

    private fun insertProduction(projectId: Int, item: Item, rate: Int) = runBlocking {
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

    private fun setProjectState(projectId: Int, state: ProjectState) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE projects SET state = ? WHERE id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, state.name)
                stmt.setInt(2, projectId)
            }
        ).process(Unit)
    }

    private fun deleteProject(projectId: Int) = runBlocking {
        DatabaseSteps.update<Int>(
            sql = SafeSQL.delete("DELETE FROM projects WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) }
        ).process(projectId)
    }
}
