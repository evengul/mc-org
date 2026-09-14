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
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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

    // ---- decommissioning (MCO-541) ----------------------------------------

    @Test
    fun `decommissioning records why and when, and coming back keeps the completion date`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId, state = "ACTIVE")
        assertEquals(HttpStatusCode.OK, patchState(projectId, "state=DONE").status)
        val completedAt = assertNotNull(getCompletedAt(projectId))

        val response = patchState(projectId, "state=DECOMMISSIONED&reason=moved+base+2026-09")

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertContains(response.bodyAsText(), "Decommissioned")
        assertEquals("DECOMMISSIONED", getState(projectId))
        assertEquals("moved base 2026-09", getDecommissionReason(projectId))
        assertNotNull(getDecommissionedAt(projectId))
        assertEquals(completedAt, getCompletedAt(projectId), "stopping is not a completion")

        assertEquals(HttpStatusCode.OK, patchState(projectId, "state=DONE").status)
        assertEquals("DONE", getState(projectId))
        assertEquals(
            completedAt,
            getCompletedAt(projectId),
            "a farm coming back online is the same farm, not a second completion",
        )
    }

    @Test
    fun `a project that was never finished cannot be decommissioned`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId, state = "ACTIVE")

        val response = patchState(projectId, "state=DECOMMISSIONED")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals("ACTIVE", getState(projectId))
    }

    @Test
    fun `a decommission reason over 200 characters is rejected`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId, state = "DONE")

        val response = patchState(projectId, "state=DECOMMISSIONED&reason=${"x".repeat(201)}")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals("DONE", getState(projectId))
        assertNull(getDecommissionReason(projectId))
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
                install(WorldParticipantPlugin)
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

    private suspend fun ApplicationTestBuilder.patchState(projectId: Int, form: String): HttpResponse =
        client.patch("/worlds/$worldId/projects/$projectId/state") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(form)
        }

    private fun getDecommissionReason(projectId: Int): String? = runBlocking {
        val result = DatabaseSteps.query<Unit, String?>(
            sql = SafeSQL.select("SELECT decommission_reason FROM projects WHERE id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next(); rs.getString("decommission_reason") }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun getDecommissionedAt(projectId: Int): java.sql.Timestamp? = runBlocking {
        val result = DatabaseSteps.query<Unit, java.sql.Timestamp?>(
            sql = SafeSQL.select("SELECT decommissioned_at FROM projects WHERE id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next(); rs.getTimestamp("decommissioned_at") }
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
