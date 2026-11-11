package app.mcorg.presentation.handler.auth

import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.pipeline.failure.SignInError
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.plugins.SeamRateLimit
import app.mcorg.presentation.router.authRouter
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertFalse
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
@Tag("database")
class GetSignInIT : WithUser() {

    @Test
    fun `Redirects to home page when already signed in`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            route("/auth") {
                authRouter()
            }
        }

        val response = client.get("/auth/sign-in") {
            val jwt = CreateTokenStep.process(user).getOrNull()!!
            cookie(AUTH_COOKIE, jwt)
        }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/worlds", response.headers["Location"])
    }

    @Test
    fun `Redirects to given page when already signed in and redirect_to is set`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            route("/auth") {
                authRouter()
            }
        }

        val response = client.get("/auth/sign-in?redirect_to=/worlds/some-page") {
            val jwt = CreateTokenStep.process(user).getOrNull()!!
            cookie(AUTH_COOKIE, jwt)
        }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/worlds/some-page", response.headers["Location"])
    }

    @Test
    fun `Shows sign-in page when not signed in`() = testApplication {
        routing {
            route("/auth") {
                authRouter()
            }
        }

        val response = client.get("/auth/sign-in")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Sign in with Microsoft")
    }

    @ParameterizedTest
    @EnumSource(SignInError::class)
    fun `Each sign-in error code shows its reason above the Microsoft button`(error: SignInError) = testApplication {
        routing { route("/auth") { authRouter() } }

        val response = client.get(error.url)
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "id=\"sign-in-error\"")
        assertContains(body, error.message)
        assertTrue(body.indexOf(error.message) < body.indexOf("Sign in with Microsoft"), "reason must sit above the button")
    }

    @Test
    fun `An unknown error code shows the generic reason and prints nothing from the URL`() = testApplication {
        routing { route("/auth") { authRouter() } }

        val response = client.get("/auth/sign-in?error=call_support_on_555-0100")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, SignInError.GENERIC.message)
        assertFalse(body.contains("555-0100"), "the page must not echo the URL's text")
    }

    @Test
    fun `No error code shows no reason`() = testApplication {
        routing { route("/auth") { authRouter() } }

        listOf("/auth/sign-in", "/auth/sign-in?error=").forEach { url ->
            val body = client.get(url).bodyAsText()
            assertFalse(body.contains("sign-in-error"), url)
        }
    }

    @Test
    fun `The sign-in callback is limited per client, and the refusal is a page`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            route("/auth") {
                authRouter()
            }
        }

        val statuses = (1..SeamRateLimit.SIGN_IN.limit).map { client.get("/auth/oidc/microsoft-redirect").status }
        assertTrue(statuses.none { it == HttpStatusCode.TooManyRequests }, "got $statuses")

        val refused = client.get("/auth/oidc/microsoft-redirect")
        assertEquals(HttpStatusCode.TooManyRequests, refused.status)
        assertContains(refused.bodyAsText(), "Too Many Requests")
    }

    @Test
    fun `The sign-in page is not limited`() = testApplication {
        routing {
            route("/auth") {
                authRouter()
            }
        }

        val statuses = (1..SeamRateLimit.SIGN_IN.limit + 5).map { client.get("/auth/sign-in").status }
        assertTrue(statuses.all { it == HttpStatusCode.OK }, "got $statuses")
    }

    @Test
    fun `Redirects to sign-out when an error occurs`() = testApplication {
        val client = createClient { followRedirects = false }

        routing {
            route("/auth") {
                authRouter()
            }
        }

        val response = client.get("/auth/sign-in") {
            cookie(AUTH_COOKIE, "invalidtoken")
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/auth/sign-out?error=invalid_token", response.headers["Location"])
    }
}