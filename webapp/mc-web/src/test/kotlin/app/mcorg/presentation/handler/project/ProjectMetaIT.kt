package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleGetProjectNameField
import app.mcorg.pipeline.project.handleUpdateProjectLocation
import app.mcorg.pipeline.project.handleUpdateProjectName
import app.mcorg.pipeline.project.handleUpdateProjectStateInline
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
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
import kotlin.test.assertNull

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ProjectMetaIT : WithUser() {

    private var worldId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld()
    }

    // ---- name -------------------------------------------------------------

    @Test
    fun `renaming a project succeeds and returns the name field`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)

        val response = client.patch("/worlds/$worldId/projects/$projectId/meta/name") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=Cobblestone Generator")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "project-meta-name-$projectId")
        assertContains(body, "Cobblestone Generator")
        assertEquals("Cobblestone Generator", getName(projectId))
    }

    @Test
    fun `too short name returns a validation error`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)

        val response = client.patch("/worlds/$worldId/projects/$projectId/meta/name") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=ab")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals("Meta IT Project", getName(projectId))
    }

    @Test
    fun `non-member cannot rename a project`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)
        val outsider = createExtraUser("outsider-meta-name")

        val response = client.patch("/worlds/$worldId/projects/$projectId/meta/name") {
            addAuthCookie(this, outsider)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=Hacked")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("Meta IT Project", getName(projectId))
    }

    @Test
    fun `name field edit mode renders an input`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)

        val response = client.get("/worlds/$worldId/projects/$projectId/meta/name?mode=edit") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "name=\"name\"")
        assertContains(body, "Meta IT Project")
    }

    // ---- location ---------------------------------------------------------

    @Test
    fun `setting location persists x and z and defaults y and dimension`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)
        assertNull(getLocationX(projectId)) // starts unset

        val response = client.patch("/worlds/$worldId/projects/$projectId/meta/location") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("x=100&z=-50")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "X: 100")
        assertContains(body, "Z: -50")
        assertEquals(100, getLocationX(projectId))
        assertEquals(-50, getLocationZ(projectId))
        assertEquals(0, getLocationY(projectId))
        assertEquals("OVERWORLD", getLocationDimension(projectId))
    }

    @Test
    fun `non-numeric coordinate returns a validation error`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)

        val response = client.patch("/worlds/$worldId/projects/$projectId/meta/location") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("x=abc&z=10")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertNull(getLocationX(projectId))
    }

    @Test
    fun `non-member cannot set location`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)
        val outsider = createExtraUser("outsider-meta-loc")

        val response = client.patch("/worlds/$worldId/projects/$projectId/meta/location") {
            addAuthCookie(this, outsider)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("x=1&z=1")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(getLocationX(projectId))
    }

    // ---- state (inline editor) -------------------------------------------

    @Test
    fun `activating a project via the meta editor succeeds`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)

        val response = client.patch("/worlds/$worldId/projects/$projectId/meta/state") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=ACTIVE")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "project-meta-state-$projectId")
        assertContains(body, "Active")
        assertEquals("ACTIVE", getState(projectId))
    }

    @Test
    fun `invalid state transition via the meta editor is rejected`() = testApplication {
        setupRoutes()
        val projectId = createProject(worldId)

        val response = client.patch("/worlds/$worldId/projects/$projectId/meta/state") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("state=PAUSED")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals("PENDING", getState(projectId))
    }

    // ---- routing ----------------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    route("/meta") {
                        get("/name") { call.handleGetProjectNameField() }
                        patch("/name") { call.handleUpdateProjectName() }
                        patch("/state") { call.handleUpdateProjectStateInline() }
                        patch("/location") { call.handleUpdateProjectLocation() }
                    }
                }
            }
        }
    }

    // ---- fixtures ---------------------------------------------------------

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "ProjectMeta IT World",
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
                        "VALUES ('Meta IT Project', ?, '', 'BUILDING', 'PLANNING', ?, NULL, NULL, NULL, NULL) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, worldId)
                stmt.setString(2, state)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun getName(projectId: Int): String =
        queryString(projectId, "SELECT name FROM projects WHERE id = ?", "name")!!
    private fun getState(projectId: Int): String =
        queryString(projectId, "SELECT state FROM projects WHERE id = ?", "state")!!
    private fun getLocationDimension(projectId: Int): String? =
        queryString(projectId, "SELECT location_dimension FROM projects WHERE id = ?", "location_dimension")
    private fun getLocationX(projectId: Int): Int? =
        queryInt(projectId, "SELECT location_x FROM projects WHERE id = ?", "location_x")
    private fun getLocationY(projectId: Int): Int? =
        queryInt(projectId, "SELECT location_y FROM projects WHERE id = ?", "location_y")
    private fun getLocationZ(projectId: Int): Int? =
        queryInt(projectId, "SELECT location_z FROM projects WHERE id = ?", "location_z")

    private fun queryString(projectId: Int, sql: String, column: String): String? = runBlocking {
        val result = DatabaseSteps.query<Unit, String?>(
            sql = SafeSQL.select(sql),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs -> rs.next(); rs.getString(column) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun queryInt(projectId: Int, sql: String, column: String): Int? = runBlocking {
        val result = DatabaseSteps.query<Unit, Int?>(
            sql = SafeSQL.select(sql),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
            resultMapper = { rs ->
                rs.next()
                val value = rs.getInt(column)
                if (rs.wasNull()) null else value
            }
        ).process(Unit)
        (result as Result.Success).value
    }
}
