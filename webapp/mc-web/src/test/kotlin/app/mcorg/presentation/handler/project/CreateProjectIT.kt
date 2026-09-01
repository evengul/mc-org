package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleCreateProject
import app.mcorg.pipeline.project.handleGetProjectList
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import io.ktor.server.routing.post
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
 * Creating a blank project.
 *
 * Written with MCO-474, which removed the `view` and `first_project` hidden fields from this
 * form along with the JavaScript that set them — the plan view was their only reader. The path
 * had no integration coverage at all, so a form that silently posted nothing would have gone
 * green. (Exactly the MCO-463 failure: a file input with no `name` shipped past passing render
 * tests.) These drive the real submit instead.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class CreateProjectIT : WithUser() {

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get { call.handleGetProjectList() }
                    post { call.handleCreateProject() }
                }
            }
        }
    }

    @Test
    fun `an HTMX submit creates the project and asks the client to reload`() = testApplication {
        setupRoutes()
        val worldId = createWorld("CreateProject IT HTMX World")

        val response = client.post("/worlds/$worldId/projects") {
            addAuthCookie(this)
            header("HX-Request", "true")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=Blank+From+Htmx&type=BUILDING")
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals("/worlds/$worldId/projects", response.headers["HX-Redirect"])
        assertEquals(listOf("Blank From Htmx"), projectNamesIn(worldId))
    }

    @Test
    fun `a plain form post creates the project and redirects`() = testApplication {
        setupRoutes()
        val worldId = createWorld("CreateProject IT Plain World")

        val response = client.post("/worlds/$worldId/projects") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=Blank+From+Form&type=BUILDING")
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        assertEquals("/worlds/$worldId/projects", response.headers["Location"])
        assertEquals(listOf("Blank From Form"), projectNamesIn(worldId))
    }

    /**
     * The two hidden fields are gone, and nothing reads them any more. A stale client that
     * still posts them must be ignored rather than 422'd on an unexpected parameter.
     */
    @Test
    fun `a stale client still posting view and first_project is not rejected`() = testApplication {
        setupRoutes()
        val worldId = createWorld("CreateProject IT Stale World")

        val response = client.post("/worlds/$worldId/projects") {
            addAuthCookie(this)
            header("HX-Request", "true")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=Stale+Client&type=BUILDING&view=plan&first_project=true")
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals("/worlds/$worldId/projects", response.headers["HX-Redirect"])
        assertEquals(listOf("Stale Client"), projectNamesIn(worldId))
    }

    /** The form the browser is actually given must carry the fields the handler requires. */
    @Test
    fun `the rendered create form posts a name and no dead hidden fields`() = testApplication {
        setupRoutes()
        val worldId = createWorld("CreateProject IT Form World")

        val body = client.get("/worlds/$worldId/projects") { addAuthCookie(this) }.bodyAsText()

        assertContains(body, "create-project-modal")
        assertContains(body, "name=\"name\"")
        assertFalse(body.contains("name=\"view\""), "the plan/exec field is gone (MCO-474)")
        assertFalse(body.contains("first-project-flag"), "its only reader is gone (MCO-474)")
        assertFalse(
            body.contains("getElementById('first-project-flag')"),
            "JS poking a removed element throws and takes showModal() with it",
        )
    }

    // ---- fixtures ---------------------------------------------------------------------

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

    private fun projectNamesIn(worldId: Int): List<String> = runBlocking {
        val result = DatabaseSteps.query<Int, List<String>>(
            sql = SafeSQL.select("SELECT name FROM projects WHERE world_id = ? ORDER BY id"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs ->
                buildList { while (rs.next()) add(rs.getString("name")) }
            },
        ).process(worldId)
        (result as Result.Success).value
    }
}
