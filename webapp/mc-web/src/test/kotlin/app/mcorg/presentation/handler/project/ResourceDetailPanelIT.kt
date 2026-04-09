package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.handleClearResourceSource
import app.mcorg.pipeline.resources.handleGetResourceDetailPanel
import app.mcorg.pipeline.resources.handleSetResourceSource
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.ResourceGatheringIdParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.delete
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
import kotlin.test.assertNull

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ResourceDetailPanelIT : WithUser() {

    private var worldId: Int = 0
    private var otherWorldId: Int = 0
    private var projectId: Int = 0
    private var siblingProjectId: Int = 0
    private var crossWorldProjectId: Int = 0
    private var resourceGatheringId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld("ResourceDetailPanel IT World")
        otherWorldId = createWorld("ResourceDetailPanel IT Other World")
        projectId = createProject(worldId, "Panel Primary")
        siblingProjectId = createProject(worldId, "Panel Sibling")
        crossWorldProjectId = createProject(otherWorldId, "Cross World")
        resourceGatheringId = createResourceGathering(projectId)
    }

    // -------------------------------------------------------------------------
    // GET /detail-panel
    // -------------------------------------------------------------------------

    @Test
    fun `GET detail-panel returns panel HTML for valid resource`() = testApplication {
        setupRoutes()
        // Reset state before this test so we are in the "not set" branch
        runBlocking { clearSource(resourceGatheringId) }

        val response = client.get(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$resourceGatheringId/detail-panel"
        ) { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Iron Ingot")
        assertContains(body, "Manual gather")
        assertContains(body, "resource-panel")
    }

    @Test
    fun `GET detail-panel unauthenticated returns redirect`() = testApplication {
        setupRoutes()
        val unauth = createClient { followRedirects = false }
        val response = unauth.get(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$resourceGatheringId/detail-panel"
        )
        assertEquals(HttpStatusCode.Found, response.status)
    }

    // -------------------------------------------------------------------------
    // PATCH /source
    // -------------------------------------------------------------------------

    @Test
    fun `PATCH source type=manual sets source_type manual`() = testApplication {
        setupRoutes()
        val rgId = createResourceGathering(projectId)

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$rgId/source"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("type=manual")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val (sourceType, solvedByProjectId) = runBlocking { readSource(rgId) }
        assertEquals("manual", sourceType)
        assertNull(solvedByProjectId)

        // OOB row fragment should be present for the plan table update
        val body = response.bodyAsText()
        assertContains(body, "plan-row-$rgId")
    }

    @Test
    fun `PATCH source type=project links project id in same world`() = testApplication {
        setupRoutes()
        val rgId = createResourceGathering(projectId)

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$rgId/source"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("type=project&projectId=$siblingProjectId")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val (sourceType, solvedByProjectId) = runBlocking { readSource(rgId) }
        assertEquals("project", sourceType)
        assertEquals(siblingProjectId, solvedByProjectId)
    }

    @Test
    fun `PATCH source type=project with project from other world is rejected`() = testApplication {
        setupRoutes()
        val rgId = createResourceGathering(projectId)

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$rgId/source"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("type=project&projectId=$crossWorldProjectId")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val (sourceType, _) = runBlocking { readSource(rgId) }
        assertNull(sourceType, "source_type must not be updated when validation fails")
    }

    @Test
    fun `PATCH source with invalid type returns 422`() = testApplication {
        setupRoutes()
        val rgId = createResourceGathering(projectId)

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$rgId/source"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("type=nonsense")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `PATCH source with missing type returns 400`() = testApplication {
        setupRoutes()
        val rgId = createResourceGathering(projectId)

        val response = client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$rgId/source"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -------------------------------------------------------------------------
    // DELETE /source
    // -------------------------------------------------------------------------

    @Test
    fun `DELETE source clears source columns`() = testApplication {
        setupRoutes()
        val rgId = createResourceGathering(projectId)
        // Pre-populate
        client.patch(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$rgId/source"
        ) {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("type=manual")
        }

        val response = client.delete(
            "/worlds/$worldId/projects/$projectId/resources/gathering/$rgId/source"
        ) { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val (sourceType, solvedByProjectId) = runBlocking { readSource(rgId) }
        assertNull(sourceType)
        assertNull(solvedByProjectId)

        val body = response.bodyAsText()
        assertContains(body, "plan-row-$rgId")
    }

    // -------------------------------------------------------------------------
    // Routing
    // -------------------------------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    route("/resources/gathering/{resourceGatheringId}") {
                        install(ResourceGatheringIdParamPlugin)
                        get("/detail-panel") { call.handleGetResourceDetailPanel() }
                        patch("/source") { call.handleSetResourceSource() }
                        delete("/source") { call.handleClearResourceSource() }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // DB fixture + read helpers
    // -------------------------------------------------------------------------

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = name,
                description = "test",
                version = MinecraftVersion.fromString("1.21.4"),
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
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

    private fun createResourceGathering(projectId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_gathering (project_id, item_id, name, required, collected) " +
                        "VALUES (?, 'minecraft:iron_ingot', 'Iron Ingot', 10, 0) RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private suspend fun readSource(rgId: Int): Pair<String?, Int?> {
        val result = DatabaseSteps.query<Int, Pair<String?, Int?>>(
            sql = SafeSQL.select("SELECT source_type, solved_by_project_id FROM resource_gathering WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs ->
                rs.next()
                val st = rs.getString("source_type")
                val pid = rs.getInt("solved_by_project_id").let { if (rs.wasNull()) null else it }
                st to pid
            }
        ).process(rgId)
        return (result as Result.Success).value
    }

    private suspend fun clearSource(rgId: Int) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE resource_gathering SET source_type = NULL, solved_by_project_id = NULL WHERE id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, rgId) }
        ).process(Unit)
    }
}
