package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.minecraft.StoreMinecraftDataStep
import app.mcorg.pipeline.project.handleDeleteProject
import app.mcorg.pipeline.project.handleUpdateProjectState
import app.mcorg.pipeline.project.resources.handleDeleteProjectProduction
import app.mcorg.pipeline.project.resources.handleUpsertProjectProduction
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.ProjectProductionItemParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.delete
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Stored demand is invalidated when world supply changes (MCO-404).
 *
 * The bug these cover: `DemandFingerprint` is checked only where demand is *written* — the project
 * page. The roadmap reads the stored rows and derives only for projects that have none, so a farm
 * reaching DONE left every other project in the world serving pre-farm numbers until somebody
 * happened to open each project page.
 *
 * Assertions are on `project_demand_state`, not on the roadmap's HTML: a missing fingerprint is
 * exactly what makes the roadmap's existing fill-on-read path re-derive, and it is the thing this
 * change controls. `WorldRoadmapIT` covers what the page does with the result.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class DemandInvalidationIT : WithUser() {

    private val version = MinecraftVersion.Release(1, 98, 0)
    private val ironIngot = Item("minecraft:iron_ingot", "Iron Ingot")
    private val poppy = Item("minecraft:poppy", "Poppy")

    private var worldId: Int = 0

    /** A farm producing iron, recorded DONE — the supply everything here turns on. */
    private var farmId: Int = 0

    /** Gathers iron: its stored plan changes when the farm's supply does. */
    private var consumerId: Int = 0

    /** Gathers poppies: nothing the farm does can change its plan. */
    private var unrelatedId: Int = 0

    @BeforeAll
    fun setup() {
        val serverData = ServerData(
            version = version,
            items = listOf(ironIngot, poppy),
            sources = listOf(
                ResourceSource(
                    type = ResourceSource.SourceType.LootTypes.BLOCK,
                    filename = "blocks/iron_ore.json",
                    producedItems = listOf(ironIngot to ResourceQuantity.ItemQuantity(1))
                )
            )
        )
        assertIs<Result.Success<*>>(runBlocking { StoreMinecraftDataStep.process(serverData) })

        worldId = createWorld("Demand Invalidation IT World")
        farmId = createProject("Iron Farm")
        consumerId = createProject("Iron-hungry Build")
        unrelatedId = createProject("Flower Garden")

        insertProduction(farmId, ironIngot.id, ironIngot.name, 3810)
        insertDemand(consumerId, ironIngot)
        insertDemand(unrelatedId, poppy)
    }

    @BeforeEach
    fun resetState() {
        setProjectState(farmId, "DONE")
        stampFingerprint(consumerId)
        stampFingerprint(unrelatedId)
        stampFingerprint(farmId)
    }

    @Test
    fun `a farm leaving DONE invalidates only the projects that gather what it makes`() = testApplication {
        setupRoutes()

        val response = client.patch("/worlds/$worldId/projects/$farmId/state") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=ACTIVE")
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertFalse(hasFingerprint(consumerId), "the iron gatherer's stored plan is now wrong")
        assertTrue(hasFingerprint(unrelatedId), "poppies have nothing to do with the iron farm")
        assertTrue(hasFingerprint(farmId), "a farm's own plan never sees its own output as supply")
    }

    @Test
    fun `a farm reaching DONE invalidates them too`() = testApplication {
        setupRoutes()
        setProjectState(farmId, "ACTIVE")
        stampFingerprint(consumerId)

        val response = client.patch("/worlds/$worldId/projects/$farmId/state") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=DONE")
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertFalse(hasFingerprint(consumerId), "iron now comes from the farm, so the plan shrank")
    }

    @Test
    fun `a transition that does not cross DONE leaves stored demand alone`() = testApplication {
        setupRoutes()
        setProjectState(farmId, "ACTIVE")
        stampFingerprint(consumerId)

        val response = client.patch("/worlds/$worldId/projects/$farmId/state") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=PAUSED")
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertTrue(
            hasFingerprint(consumerId),
            "ACTIVE to PAUSED changes nothing about what the world supplies",
        )
    }

    @Test
    fun `removing a produced item from an operational farm invalidates its consumers`() = testApplication {
        setupRoutes()
        val productionId = productionIdOf(farmId, ironIngot.id)

        val response = client.delete("/worlds/$worldId/projects/$farmId/productions/$productionId") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertFalse(hasFingerprint(consumerId), "the world stopped supplying iron")
        assertTrue(hasFingerprint(unrelatedId))

        insertProduction(farmId, ironIngot.id, ironIngot.name, 3810)
    }

    @Test
    fun `adding a produced item to a farm that is not operational invalidates nothing`() = testApplication {
        setupRoutes()
        setProjectState(farmId, "ACTIVE")
        stampFingerprint(consumerId)

        val response = client.post("/worlds/$worldId/projects/$farmId/productions") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("itemId=${poppy.id}&ratePerHour=12")
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertTrue(
            hasFingerprint(consumerId),
            "a farm that is not DONE supplies nothing, so its productions cannot make a plan wrong",
        )

        deleteProduction(farmId, poppy.id)
    }

    @Test
    fun `deleting an operational farm invalidates its consumers`() = testApplication {
        setupRoutes()
        val doomedFarmId = createProject("Second Iron Farm")
        setProjectState(doomedFarmId, "DONE")
        insertProduction(doomedFarmId, ironIngot.id, ironIngot.name, 1000)

        val response = client.delete("/worlds/$worldId/projects/$doomedFarmId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        // Read before the cascade, not after: once the project is gone so are its productions,
        // and nothing could tell which items stopped being supplied.
        assertFalse(hasFingerprint(consumerId), "iron stopped being supplied when the farm went")
        assertTrue(hasFingerprint(unrelatedId))
    }

    // ---- routing — mirrors WorldHandler ---------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    delete { call.handleDeleteProject() }
                    patch("/state") { call.handleUpdateProjectState() }
                    route("/productions") {
                        post { call.handleUpsertProjectProduction() }
                        route("/{productionId}") {
                            install(ProjectProductionItemParamPlugin)
                            delete { call.handleDeleteProjectProduction() }
                        }
                    }
                }
            }
        }
    }

    // ---- fixtures -------------------------------------------------------------------

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = version)
        )
        (result as Result.Success).value
    }

    private fun createProject(name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'FARMING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun setProjectState(projectId: Int, state: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE projects SET state = ? WHERE id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, state)
                stmt.setInt(2, projectId)
            }
        ).process(Unit)
    }

    private fun insertProduction(projectId: Int, itemId: String, name: String, rate: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_productions (project_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT (project_id, item_id) DO UPDATE SET rate_per_hour = EXCLUDED.rate_per_hour"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
                stmt.setInt(4, rate)
            }
        ).process(Unit)
    }

    private fun deleteProduction(projectId: Int, itemId: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.delete("DELETE FROM project_productions WHERE project_id = ? AND item_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
            }
        ).process(Unit)
    }

    private fun productionIdOf(projectId: Int, itemId: String): Int = runBlocking {
        val result = DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.select("SELECT id FROM project_productions WHERE project_id = ? AND item_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
            },
            resultMapper = { rs -> rs.next(); rs.getInt("id") }
        ).process(Unit)
        (result as Result.Success).value
    }

    /** One demand row, standing in for "this project's plan touches this item". */
    private fun insertDemand(projectId: Int, item: Item) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_demand (project_id, item_id, item_name, quantity, activity_group, node_status) " +
                    "VALUES (?, ?, ?, 64, 'GATHER', 'RESOLVED')"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, item.id)
                stmt.setString(3, item.name)
            }
        ).process(Unit)
    }

    private fun stampFingerprint(projectId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_demand_state (project_id, fingerprint, derived_at) VALUES (?, 'fp-test', now()) " +
                    "ON CONFLICT (project_id) DO UPDATE SET fingerprint = EXCLUDED.fingerprint"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) }
        ).process(Unit)
    }

    private fun hasFingerprint(projectId: Int): Boolean = runBlocking {
        val result = DatabaseSteps.query<Unit, Boolean>(
            sql = SafeSQL.select("SELECT 1 FROM project_demand_state WHERE project_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next() }
        ).process(Unit)
        (result as Result.Success).value
    }
}
