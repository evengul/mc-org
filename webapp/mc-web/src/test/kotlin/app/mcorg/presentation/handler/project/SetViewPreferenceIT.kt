package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.viewpreference.handleSetViewPreference
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class SetViewPreferenceIT : WithUser() {

    private var worldId: Int = 0
    private var projectId: Int = 0

    @BeforeAll
    fun setup() {
        val worldResult = runBlocking {
            CreateWorldStep(user).process(
                CreateWorldInput(
                    name = "ViewPref IT World",
                    description = "test",
                    version = MinecraftVersion.fromString("1.21.4")
                )
            )
        }
        worldId = (worldResult as Result.Success).value

        val projectResult = runBlocking {
            DatabaseSteps.update<Unit>(
                sql = SafeSQL.insert("INSERT INTO projects (name, world_id, type, stage) VALUES ('IT Project', ?, 'build', 'planning') RETURNING id"),
                parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
            ).process(Unit)
        }
        projectId = (projectResult as Result.Success).value
    }

    @Test
    fun `should return 200 with empty body for valid preference`() = testApplication {
        routing {
            route("/worlds/{worldId}") {
                install(AuthPlugin)
                install(WorldParamPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    post("/view-preference") {
                        call.handleSetViewPreference()
                    }
                }
            }
        }

        val response = client.post("/worlds/$worldId/projects/$projectId/view-preference") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("preference=plan")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `should return error for invalid preference`() = testApplication {
        routing {
            route("/worlds/{worldId}") {
                install(AuthPlugin)
                install(WorldParamPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    post("/view-preference") {
                        call.handleSetViewPreference()
                    }
                }
            }
        }

        val response = client.post("/worlds/$worldId/projects/$projectId/view-preference") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("preference=invalid")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
