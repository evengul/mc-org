package app.mcorg.presentation.handler.auth

import app.mcorg.pipeline.auth.CreateTokenStep
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.router.authRouter
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
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
        assertEquals("/app", response.headers["Location"])
    }

    @Test
    fun `Redirects to given page when already signed in and redirect_to is set`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            route("/auth") {
                authRouter()
            }
        }

        val response = client.get("/auth/sign-in?redirect_to=/app/some-page") {
            val jwt = CreateTokenStep.process(user).getOrNull()!!
            cookie(AUTH_COOKIE, jwt)
        }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/app/some-page", response.headers["Location"])
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
        assertEquals("/auth/sign-out?error=conversion&args=", response.headers["Location"])
    }
}