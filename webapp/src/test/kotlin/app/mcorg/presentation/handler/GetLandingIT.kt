package app.mcorg.presentation.handler

import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetLandingIT : WithUser() {
    @Test
    fun `Redirect on missing token`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            get("/") {
                call.handleGetLanding()
            }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/auth/sign-in", response.headers["Location"])
    }

    @Test
    fun `Redirect to sign-in on error`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            get("/") {
                call.handleGetLanding()
            }
        }

        val response = client.get("/") {
            cookie(AUTH_COOKIE, "invalid_token")
        }
        assertEquals(HttpStatusCode.Found, response.status)
        assertContains(response.headers["Location"] ?: "", "/auth/sign-out?error=")
    }

    @Test
    fun `Redirect to app on success`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            get("/") {
                call.handleGetLanding()
            }
        }

        val response = client.get("/") {
            val jwt = CreateTokenStep.process(user).getOrNull()!!
            cookie(AUTH_COOKIE, jwt)
        }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/app", response.headers["Location"])
    }
}