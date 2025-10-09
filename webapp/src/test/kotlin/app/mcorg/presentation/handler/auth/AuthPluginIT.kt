package app.mcorg.presentation.handler.auth

import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.HttpClient
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class AuthPluginIT : WithUser() {
    @Test
    fun `Should allow all static paths`() = testApplication {
        val client = setup()

        val response = client.get("/static/file.txt")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `Should allow all asset paths`() = testApplication {
        val client = setup()

        val response = client.get("/assets/image.png")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `Should allow favicon path`() = testApplication {
        val client = setup()

        val response = client.get("/favicon.ico")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `Should send to sign out if token is invalid`() = testApplication {
        val client = setup()

        val response = client.get("/some-protected-path") {
            cookie(AUTH_COOKIE, "invalid-token")
        }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/auth/sign-out?error=conversion&args=", response.headers["Location"])
    }

    @Test
    fun `Should send to sign in if no token`() = testApplication {
        val client = setup()

        val response = client.get("/some-protected-path")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/auth/sign-in?redirect_to=/some-protected-path", response.headers["Location"])
    }

    @Test
    fun `Should allow access to protected path with valid token`() = testApplication {
        val client = setup()

        val response = client.get("/some-protected-path") {
            addAuthCookie(this)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("protected content", response.bodyAsText())
    }

    private fun ApplicationTestBuilder.setup(): HttpClient {
        routing {
            install(AuthPlugin)
            get("/static/file.txt") {
                call.respond(HttpStatusCode.OK)
            }
            get("/assets/image.png") {
                call.respond(HttpStatusCode.OK)
            }
            get("/favicon.ico") {
                call.respond(HttpStatusCode.OK)
            }
            get("/some-protected-path") {
                call.respond(HttpStatusCode.OK, "protected content")
            }
        }

        return createClient { followRedirects = false }
    }
}