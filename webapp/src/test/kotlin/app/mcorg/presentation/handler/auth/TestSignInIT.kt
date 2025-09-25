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
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class TestSignInIT {
    @BeforeEach
    fun setup() {
        AppConfig.env = app.mcorg.domain.Test
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

        val response = client.get("/auth/oidc/test-redirect")
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

        val response = client.get("/auth/oidc/test-redirect?redirect_to=/custom-path")
        assert(response.status == HttpStatusCode.Found)
        assert(response.headers["Location"] == "/custom-path")
    }

    @Test
    fun `Respond with 403 when sign in fails`() = testApplication {
        AppConfig.env = Local

        val client = createClient { followRedirects = false }
        routing {
            route("/auth") {
                authRouter()
            }
        }

        val response = client.get("/auth/oidc/test-redirect")
        assert(response.status == HttpStatusCode.Forbidden)
    }
}