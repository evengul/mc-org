package app.mcorg.presentation.handler.auth

import app.mcorg.config.AppConfig
import app.mcorg.domain.Local
import app.mcorg.domain.Production
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.router.authRouter
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.setCookie
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
class DemoSignInIT {
    @BeforeEach
    fun setup() {
        AppConfig.env = Local
        // Explicit since MCO-333 removed the hardcoded "evegul" default: with DEMO_USER unset,
        // demo sign-in correctly fails closed, which is a different test than this one.
        AppConfig.demoUser = "demo-user"
    }

    /**
     * One test below sets `env = Production`, and surefire reuses a single JVM across the whole
     * database tier — so leaving it set would hand every later test class a production AppConfig
     * (MCO-379). Restored here rather than relying on the next class's own setup.
     */
    @AfterEach
    fun restoreEnv() {
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

    // --- MCO-352 ---------------------------------------------------------------------------

    @Test
    fun `demo sign-in does not exist in production`() = testApplication {
        // The route is registered unconditionally and AuthPlugin lets /oidc through
        // unauthenticated, so before this anyone who typed the URL on app.seam.gg received an
        // eight-hour cookie for the shared demo account. getSignInUrl declining to *offer* the
        // path was the only thing standing in the way, and that is not a control.
        AppConfig.env = Production
        val client = createClient { followRedirects = false }

        routing {
            route("/auth") {
                authRouter()
            }
        }

        val response = client.get("/auth/oidc/demo-redirect")

        // 404, not 403: in production the endpoint should not appear to exist at all.
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertNull(
            response.setCookie().find { it.name == AUTH_COOKIE },
            "a refused demo sign-in must not issue an auth cookie",
        )
    }

    @Test
    fun `an off-origin redirect target is refused`() = testApplication {
        val client = createClient { followRedirects = false }

        routing {
            route("/auth") {
                authRouter()
            }
        }

        val offOrigin = listOf(
            "https://evil.example",
            "//evil.example",
            "javascript:alert(1)",
            "/\\evil.example",
        )

        offOrigin.forEach { target ->
            val response = client.get("/auth/oidc/demo-redirect?redirect_to=${target.encodeURLParameter()}")
            assertEquals(HttpStatusCode.Found, response.status, "target $target")
            assertEquals("/", response.headers["Location"], "target $target should fall back to /")
        }
    }
}