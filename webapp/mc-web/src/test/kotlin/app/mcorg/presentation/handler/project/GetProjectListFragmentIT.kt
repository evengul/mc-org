package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.handleGetProjectListFragment
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetProjectListFragmentIT : WithUser() {

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = name,
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                        "VALUES (?, ?, '', 'BUILDING', 'IDEA', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    @Test
    fun `returns execute view fragment when view=execute`() = testApplication {
        val worldId = createWorld("Fragment IT Execute World")
        createProject(worldId, "Execute Fragment Project")

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get("/list-fragment") {
                        call.handleGetProjectListFragment()
                    }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects/list-fragment?view=execute") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "projects-view")
        assertContains(body, "project-card-list")
        assertContains(body, "Execute Fragment Project")
    }

    @Test
    fun `returns plan view fragment when view=plan`() = testApplication {
        val worldId = createWorld("Fragment IT Plan World")
        createProject(worldId, "Plan Fragment Project")

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get("/list-fragment") {
                        call.handleGetProjectListFragment()
                    }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects/list-fragment?view=plan") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "projects-view")
        assertContains(body, "project-card-list")
        assertContains(body, "Plan Fragment Project")
        assertContains(body, "No resources defined")
    }

    @Test
    fun `defaults to execute view when view param is missing`() = testApplication {
        val worldId = createWorld("Fragment IT Default World")
        createProject(worldId, "Default Fragment Project")

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get("/list-fragment") {
                        call.handleGetProjectListFragment()
                    }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects/list-fragment") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "projects-view")
        assertContains(body, "project-card-list")
        assertContains(body, "Default Fragment Project")
    }

    @Test
    fun `defaults to execute view when view param is invalid`() = testApplication {
        val worldId = createWorld("Fragment IT Invalid World")
        createProject(worldId, "Invalid View Fragment Project")

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get("/list-fragment") {
                        call.handleGetProjectListFragment()
                    }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects/list-fragment?view=invalid") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "projects-view")
    }

    @Test
    fun `redirects to sign in when not authenticated`() = testApplication {
        val worldId = createWorld("Fragment IT Auth World")
        val client = createClient { followRedirects = false }

        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects") {
                    get("/list-fragment") {
                        call.handleGetProjectListFragment()
                    }
                }
            }
        }

        val response = client.get("/worlds/$worldId/projects/list-fragment")

        assertEquals(HttpStatusCode.Found, response.status)
    }
}
