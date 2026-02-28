package app.mcorg.presentation.handler.auth

import app.mcorg.config.AppConfig
import app.mcorg.domain.Local
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.router.authRouter
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.setCookie
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class DemoSignInIT {
    @BeforeEach
    fun setup() {
        AppConfig.env = Local
    }

    @Test
    fun `Redirect to landing page with cookie when signed in successfully`() = testApplication {
        val client = createClient {
            followRedirects = false
        }

        routing {
            route("/auth") {
                authRouter()
            }
        }

        val response = client.get("/auth/oidc/demo-redirect")
        assert(response.status == HttpStatusCode.Found)
        assert(response.headers["Location"] == "/")
        val cookie = response.setCookie().find { it.name == AUTH_COOKIE }
        assertNotNull(cookie)
        assert(cookie.value.isNotEmpty()) { "Auth cookie should not be empty" }
    }

    @Test
    fun `Redirect to custom URL when sign in with redirect URL`() = testApplication {
        val client = createClient {
            followRedirects = false
        }

        routing {
            route("/auth") {
                authRouter()
            }
        }

        val response = client.get("/auth/oidc/demo-redirect?redirect_to=/custom-path")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/custom-path", response.headers["Location"])
    }
}