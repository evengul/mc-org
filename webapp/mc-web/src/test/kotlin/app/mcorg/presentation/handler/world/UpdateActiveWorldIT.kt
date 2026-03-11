package app.mcorg.presentation.handler.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.setCookie
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class UpdateActiveWorldIT : WithUser() {

    private var worldId: Int = 0

    @BeforeAll
    fun setup() {
        val result = runBlocking {
            CreateWorldStep(user).process(
                CreateWorldInput(
                    name = "ActiveWorld IT World",
                    description = "test",
                    version = MinecraftVersion.fromString("1.21.4")
                )
            )
        }
        worldId = (result as Result.Success).value
    }

    @Test
    fun `Reissues auth cookie when worldId differs from activeWorldId`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                get { call.respond(HttpStatusCode.OK) }
            }
        }

        val response = client.get("/worlds/$worldId") {
            addAuthCookie(this, user.copy(activeWorldId = worldId + 9999))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.setCookie().find { it.name == AUTH_COOKIE }, "Auth cookie should be reissued")
    }

    @Test
    fun `Reissues auth cookie when activeWorldId is null`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                get { call.respond(HttpStatusCode.OK) }
            }
        }

        val response = client.get("/worlds/$worldId") {
            addAuthCookie(this, user.copy(activeWorldId = null))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.setCookie().find { it.name == AUTH_COOKIE }, "Auth cookie should be reissued when activeWorldId was null")
    }

    @Test
    fun `Does not reissue auth cookie when worldId matches activeWorldId`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(UpdateActiveWorldPlugin)
                get { call.respond(HttpStatusCode.OK) }
            }
        }

        val response = client.get("/worlds/$worldId") {
            addAuthCookie(this, user.copy(activeWorldId = worldId))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.setCookie().find { it.name == AUTH_COOKIE }, "Auth cookie should not be reissued when worldId matches")
    }
}
