package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleUpdateProjectState
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ProjectStateIT : WithUser() {

    private var worldId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld()
    }

    @Test
    fun `activating a pending project succeeds and returns badge fragment`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)

        val response = client.patch("/worlds/$worldId/projects/$projectId/state") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=ACTIVE")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "project-state-badge-$projectId")
        assertContains(body, "Active")
        assertEquals("ACTIVE", getState(projectId))
    }

    @Test
    fun `pausing a pending project is an invalid transition`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)

        val response = client.patch("/worlds/$worldId/projects/$projectId/state") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=PAUSED")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals("PENDING", getState(projectId))
    }

    @Test
    fun `completing an active project sets completed_at`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId, state = "ACTIVE")
        assertNull(getCompletedAt(projectId))

        val response = client.patch("/worlds/$worldId/projects/$projectId/state") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=DONE")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("DONE", getState(projectId))
        assertNotNull(getCompletedAt(projectId))
    }

    @Test
    fun `unknown state value returns validation error`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)

        val response = client.patch("/worlds/$worldId/projects/$projectId/state") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=BOGUS")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `missing state parameter returns validation error`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)

        val response = client.patch("/worlds/$worldId/projects/$projectId/state") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `non-member cannot change project state`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)
        val outsider = createExtraUser("outsider-state")

        val response = client.patch("/worlds/$worldId/projects/$projectId/state") {
            addAuthCookie(this, outsider)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=ACTIVE")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("PENDING", getState(projectId))
    }

    @Test
    fun `unauthenticated request is redirected`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)

        val unauthClient = createClient { followRedirects = false }
        val response = unauthClient.patch("/worlds/$worldId/projects/$projectId/state") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=ACTIVE")
        }

        assertEquals(HttpStatusCode.Found, response.status)
    }

    // -------------------------------------------------------------------------
    // Routing helper
    // -------------------------------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    patch("/state") { call.handleUpdateProjectState() }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // DB fixture helpers
    // -------------------------------------------------------------------------

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "ProjectState IT World",
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int, state: String = "PENDING"): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                        "VALUES ('State IT Project', ?, '', 'BUILDING', 'PLANNING', ?, 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, worldId)
                stmt.setString(2, state)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun getState(projectId: Int): String = runBlocking {
        val result = DatabaseSteps.query<Unit, String>(
            sql = SafeSQL.select("SELECT state FROM projects WHERE id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next(); rs.getString("state") }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun getCompletedAt(projectId: Int): java.sql.Timestamp? = runBlocking {
        val result = DatabaseSteps.query<Unit, java.sql.Timestamp?>(
            sql = SafeSQL.select("SELECT completed_at FROM projects WHERE id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next(); rs.getTimestamp("completed_at") }
        ).process(Unit)
        (result as Result.Success).value
    }
}
