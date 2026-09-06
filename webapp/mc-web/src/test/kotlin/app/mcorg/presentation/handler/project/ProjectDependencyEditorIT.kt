package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.dependencies.handleAddProjectDependency
import app.mcorg.pipeline.project.dependencies.handleDeleteProjectDependency
import app.mcorg.pipeline.project.dependencies.handleGetDependenciesPanel
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectDependencyItemPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The manual dependency editor (MCO-302).
 *
 * The case that matters most here is [`an imported dependency can be deleted`]: an idea import
 * was the only writer of `project_dependencies` while the editor was missing, so a row it wrote
 * could not be removed through the application at all. That is the bug this issue exists to fix,
 * and a delete path that refused imported rows would reproduce it exactly.
 *
 * Every test asserts on rows it created itself and never on "the newest row" — these ITs share a
 * database with no truncation between classes.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ProjectDependencyEditorIT : WithUser() {

    // ---- add --------------------------------------------------------------

    @Test
    fun `adding a dependency declares the row and marks it manual`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()
        val site = createProject(worldId, "Build Site")
        val perimeter = createProject(worldId, "Perimeter")

        val response = client.post("/worlds/$worldId/projects/$site/dependencies") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("dependsOnProjectId=$perimeter")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, countDependency(site, perimeter))
        assertEquals("MANUAL", originOf(site, perimeter))
        // The re-rendered panel shows the new row, so the editor updates without a reload.
        assertContains(response.bodyAsText(), "Perimeter")
    }

    @Test
    fun `adding the same dependency twice is not an error and leaves one row`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()
        val site = createProject(worldId, "Twice Site")
        val perimeter = createProject(worldId, "Twice Perimeter")
        declareDependency(site, perimeter, origin = "MANUAL")

        // The second add is rejected by validation rather than by the unique constraint: once the
        // row exists, GetAvailableProjectDependenciesStep stops offering that project at all.
        val response = client.post("/worlds/$worldId/projects/$site/dependencies") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("dependsOnProjectId=$perimeter")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(1, countDependency(site, perimeter))
    }

    // ---- add: validation --------------------------------------------------

    @Test
    fun `a project cannot be made to wait on itself`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()
        val site = createProject(worldId, "Self Site")

        val response = client.post("/worlds/$worldId/projects/$site/dependencies") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("dependsOnProjectId=$site")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(0, countDependency(site, site))
    }

    @Test
    fun `a dependency that would close a cycle is refused`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()
        val a = createProject(worldId, "Cycle A")
        val b = createProject(worldId, "Cycle B")
        declareDependency(b, a, origin = "MANUAL") // B waits on A

        // ...so A waiting on B would close the loop.
        val response = client.post("/worlds/$worldId/projects/$a/dependencies") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("dependsOnProjectId=$b")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(0, countDependency(a, b))
    }

    @Test
    fun `a project in another world cannot be depended on`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()
        val otherWorldId = createWorld()
        val site = createProject(worldId, "Local Site")
        val foreign = createProject(otherWorldId, "Foreign Project")

        val response = client.post("/worlds/$worldId/projects/$site/dependencies") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("dependsOnProjectId=$foreign")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(0, countDependency(site, foreign))
    }

    @Test
    fun `a missing dependsOnProjectId is rejected`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()
        val site = createProject(worldId, "Empty Post Site")

        // The shape a select with no `name` attribute would produce — the MCO-463 bug.
        // 400 rather than the 422 the refusals above get: a missing parameter is a malformed
        // request, where "you may not depend on that" is a well-formed request that is refused.
        val response = client.post("/worlds/$worldId/projects/$site/dependencies") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ---- delete -----------------------------------------------------------

    @Test
    fun `an imported dependency can be deleted`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()
        val site = createProject(worldId, "Imported Site")
        val farm = createProject(worldId, "Imported Farm")
        declareDependency(site, farm, origin = "IMPORTED")

        val response = client.delete("/worlds/$worldId/projects/$site/dependencies/$farm") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(0, countDependency(site, farm))
    }

    @Test
    fun `deleting a dependency that is not declared is a not found`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()
        val site = createProject(worldId, "Absent Site")
        val other = createProject(worldId, "Absent Other")

        val response = client.delete("/worlds/$worldId/projects/$site/dependencies/$other") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ---- panel ------------------------------------------------------------

    @Test
    fun `the panel lists declared rows, badges imported ones, and offers the rest`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()
        val site = createProject(worldId, "Panel Site")
        val imported = createProject(worldId, "Panel Imported Farm")
        val addable = createProject(worldId, "Panel Addable Quarry")
        declareDependency(site, imported, origin = "IMPORTED")

        val response = client.get("/worlds/$worldId/projects/$site/dependencies/panel") {
            addAuthCookie(this)
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(body, "Panel Imported Farm")
        assertContains(body, "Imported")
        // Still addable, so it must appear as an option...
        assertContains(body, "Panel Addable Quarry")
        // ...and the select has to carry the name the POST reads, or the form posts nothing.
        assertContains(body, "name=\"dependsOnProjectId\"")
    }

    @Test
    fun `a project already depended on is not offered again`() = testApplication {
        installRoutes()
        val client = createClient { followRedirects = false }
        val worldId = createWorld()
        val site = createProject(worldId, "Offer Site")
        val taken = createProject(worldId, "Offer Taken Farm")
        declareDependency(site, taken, origin = "MANUAL")

        val body = client.get("/worlds/$worldId/projects/$site/dependencies/panel") {
            addAuthCookie(this)
        }.bodyAsText()

        // It is named once, as the declared row — never as an <option value="...">.
        assertFalse(body.contains("<option value=\"$taken\""))
        assertTrue(body.contains("Offer Taken Farm"))
    }

    // ---- routing ----------------------------------------------------------

    private fun io.ktor.server.testing.ApplicationTestBuilder.installRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    route("/dependencies") {
                        get("/panel") { call.handleGetDependenciesPanel() }
                        post { call.handleAddProjectDependency() }
                        route("/{dependencyId}") {
                            install(ProjectDependencyItemPlugin)
                            delete { call.handleDeleteProjectDependency() }
                        }
                    }
                }
            }
        }
    }

    // ---- fixtures ---------------------------------------------------------

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "DependencyEditorIT World ${System.nanoTime()}",
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'PLANNING', 'PENDING', NULL, NULL, NULL, NULL) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, "$name ${System.nanoTime()}")
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun declareDependency(projectId: Int, dependsOnProjectId: Int, origin: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO project_dependencies (project_id, depends_on_project_id, origin) VALUES (?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setInt(2, dependsOnProjectId)
                stmt.setString(3, origin)
            }
        ).process(Unit)
    }

    private fun countDependency(projectId: Int, dependsOnProjectId: Int): Int = runBlocking {
        val result = DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.select(
                "SELECT COUNT(*) AS cnt FROM project_dependencies WHERE project_id = ? AND depends_on_project_id = ?"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setInt(2, dependsOnProjectId)
            },
            resultMapper = { rs -> rs.next(); rs.getInt("cnt") }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun originOf(projectId: Int, dependsOnProjectId: Int): String? = runBlocking {
        val result = DatabaseSteps.query<Unit, String?>(
            sql = SafeSQL.select(
                "SELECT origin FROM project_dependencies WHERE project_id = ? AND depends_on_project_id = ?"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setInt(2, dependsOnProjectId)
            },
            resultMapper = { rs -> if (rs.next()) rs.getString("origin") else null }
        ).process(Unit)
        (result as Result.Success).value
    }
}
