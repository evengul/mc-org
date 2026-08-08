package app.mcorg.presentation.handler.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.commonsteps.UpdateProjectStageStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.roadmap.handleGetWorldRoadmap
import app.mcorg.presentation.plugins.AuthPlugin
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
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * `GET /worlds/{worldId}/roadmap` (MCO-288): the derived dependency table, its empty state,
 * and the membership gate.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class WorldRoadmapIT : WithUser() {

    @Test
    fun `the roadmap names the blocking project and the resource it owes`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Roadmap IT World")
        val consumer = createProject(worldId, "Beacon Build")
        val farm = createProject(worldId, "Iron Farm")
        createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot")
        createProduction(farm, "minecraft:iron_ingot", "Iron Ingot")

        val response = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "roadmap-table")
        assertContains(body, "Beacon Build")
        assertContains(body, "Iron Farm")
        // The IA asks the blocked-by cell to name both halves.
        assertContains(body, "Iron Ingot")
        assertContains(body, "/worlds/$worldId/projects/$farm")

        deleteWorld(worldId)
    }

    @Test
    fun `an operational farm still appears, no longer blocking`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Operational Roadmap World")
        val consumer = createProject(worldId, "Beacon Build")
        val farm = createProject(worldId, "Iron Farm")
        createRequirement(consumer, "minecraft:iron_ingot", "Iron Ingot")
        createProduction(farm, "minecraft:iron_ingot", "Iron Ingot")
        runBlocking { UpdateProjectStageStep(farm).process(ProjectStage.COMPLETED) }

        val body = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }.bodyAsText()

        // The relationship is still on the roadmap — it just stopped being a blocker, which
        // the summary line reports (it only counts blocked projects when there are any).
        assertContains(body, "Iron Farm")
        assertContains(body, "2 projects")
        assertFalse(body.contains("1 blocked"), "an operational farm blocks nobody")

        deleteWorld(worldId)
    }

    @Test
    fun `a world with no projects gets the empty state, not a bare table`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Empty Roadmap World")

        val response = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Nothing to sequence yet")
        assertContains(body, "/worlds/$worldId/projects")
        assertFalse(body.contains("roadmap-table"), "no table until there is something to sequence")

        deleteWorld(worldId)
    }

    @Test
    fun `a non-member cannot read the roadmap`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Private Roadmap World")
        val outsider = createExtraUser("roadmap-outsider")

        val response = client.get("/worlds/$worldId/roadmap") { addAuthCookie(this, outsider) }

        assertEquals(HttpStatusCode.Forbidden, response.status)

        deleteWorld(worldId)
    }

    @Test
    fun `unauthenticated requests redirect`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Anon Roadmap World")
        val unauth = createClient { followRedirects = false }

        val response = unauth.get("/worlds/$worldId/roadmap")

        assertEquals(HttpStatusCode.Found, response.status)

        deleteWorld(worldId)
    }

    // ---- routing — mirrors WorldHandler ------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                get("/roadmap") { call.handleGetWorldRoadmap() }
            }
        }
    }

    // ---- fixtures ------------------------------------------------------------------

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = MinecraftVersion.fromString("1.20.1"))
        )
        (result as Result.Success).value
    }

    private fun deleteWorld(worldId: Int) = runBlocking {
        DatabaseSteps.update<Int>(
            SafeSQL.delete("DELETE FROM world WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) }
        ).process(worldId)
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createRequirement(projectId: Int, itemId: String, name: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, 32)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
            }
        ).process(Unit)
    }

    private fun createProduction(projectId: Int, itemId: String, name: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO project_productions (project_id, item_id, name, rate_per_hour) VALUES (?, ?, ?, 0)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
            }
        ).process(Unit)
    }
}
