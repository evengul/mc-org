package app.mcorg.presentation.handler.auth

import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.HttpClient
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.setCookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
@Tag("database")
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
        assertEquals("/auth/sign-out?error=invalid_token", response.headers["Location"])
    }

    @Test
    fun `An HTMX request with an invalid token is redirected with HX-Redirect, not a 302`() = testApplication {
        val client = setup()

        val response = client.get("/some-protected-path") {
            cookie(AUTH_COOKIE, "invalid-token")
            header("HX-Request", "true")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("/auth/sign-out?error=invalid_token", response.headers["HX-Redirect"])
        assertNull(response.headers["Location"])
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `An invalid token clears the cookie with or without HTMX`() = testApplication {
        val client = setup()

        listOf(false, true).forEach { htmx ->
            val response = client.get("/some-protected-path") {
                cookie(AUTH_COOKIE, "invalid-token")
                if (htmx) header("HX-Request", "true")
            }
            val cleared = response.setCookie().singleOrNull { it.name == AUTH_COOKIE }
            assertNotNull(cleared, "htmx=$htmx should clear the auth cookie")
            assertEquals("", cleared.value)
            assertEquals(0, cleared.maxAge)
        }
    }

    @Test
    fun `Should send to sign in if no token`() = testApplication {
        val client = setup()

        val response = client.get("/some-protected-path")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/auth/sign-in?redirect_to=%2Fsome-protected-path", response.headers["Location"])
    }

    @Test
    fun `A plain request keeps its whole query string through sign-in`() = testApplication {
        val client = setup()

        val response = client.get("/some-protected-path?tab=tasks&drill=minecraft%3Adiamond")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/some-protected-path?tab=tasks&drill=minecraft%3Adiamond", redirectTo(response.headers["Location"]))
    }

    @Test
    fun `An HTMX request with no token is sent to sign in, back to the page it was clicked on`() = testApplication {
        val client = setup()

        val response = client.post("/some-fragment-endpoint") {
            header("HX-Request", "true")
            header("HX-Current-URL", "http://localhost/worlds/3/projects/43?tab=tasks&drill=minecraft%3Adiamond")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.headers["Location"])
        assertEquals("/worlds/3/projects/43?tab=tasks&drill=minecraft%3Adiamond", redirectTo(response.headers["HX-Redirect"]))
    }

    @Test
    fun `An ampersand in the current page's path cannot add a parameter of its own`() = testApplication {
        val client = setup()

        val response = client.post("/some-fragment-endpoint") {
            header("HX-Request", "true")
            header("HX-Current-URL", "http://localhost/worlds/3/a&redirect_to=/elsewhere")
        }
        assertEquals("/worlds/3/a&redirect_to=/elsewhere", redirectTo(response.headers["HX-Redirect"]))
    }

    @Test
    fun `An HTMX request with no token and no usable current URL falls back to its own URI`() = testApplication {
        val client = setup()

        listOf(null, "not a url", "relative/path", "http://localhost").forEach { currentUrl ->
            val response = client.get("/some-protected-path?tab=tasks") {
                header("HX-Request", "true")
                if (currentUrl != null) header("HX-Current-URL", currentUrl)
            }
            assertEquals("/some-protected-path?tab=tasks", redirectTo(response.headers["HX-Redirect"]), "HX-Current-URL=$currentUrl")
        }
    }

    @Test
    fun `A plain request ignores HX-Current-URL`() = testApplication {
        val client = setup()

        val response = client.get("/some-protected-path") {
            header("HX-Current-URL", "http://localhost/worlds/3")
        }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/some-protected-path", redirectTo(response.headers["Location"]))
    }

    /** The one `redirect_to` the sign-in page would read, decoded, or a failure if there are several. */
    private fun redirectTo(location: String?): String? {
        assertNotNull(location)
        val url = Url(location)
        assertEquals("/auth/sign-in", url.encodedPath)
        return url.parameters.getAll("redirect_to")?.single()
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
            post("/some-fragment-endpoint") {
                call.respond(HttpStatusCode.OK, "fragment")
            }
        }

        return createClient { followRedirects = false }
    }
}