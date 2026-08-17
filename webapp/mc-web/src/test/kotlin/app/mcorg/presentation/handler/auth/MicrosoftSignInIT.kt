package app.mcorg.presentation.handler.auth

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.router.authRouter
import app.mcorg.presentation.security.OAUTH_STATE_COOKIE
import app.mcorg.test.postgres.DatabaseTestExtension
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.setCookie
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URLEncoder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@WireMockTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
@Tag("database")
class MicrosoftSignInIT {

    private lateinit var wireMock: WireMock

    @BeforeEach
    fun setup(wiremock: WireMockRuntimeInfo) {
        wireMock = wiremock.wireMock

        AppConfig.env = Production
        AppConfig.appHost = "app.seam.gg"
        AppConfig.skipMicrosoftSignIn = false

        AppConfig.microsoftLoginBaseUrl = wiremock.httpBaseUrl
        AppConfig.xboxAuthBaseUrl = wiremock.httpBaseUrl
        AppConfig.xstsAuthBaseUrl = wiremock.httpBaseUrl
        AppConfig.minecraftBaseUrl = wiremock.httpBaseUrl
    }

    @Test
    fun `Redirect to landing page with cookie when signed in successfully`() = testApplication {
        val client = createClient { followRedirects = false }

        val microsoftCode = "code123"

        routing {
            route("/auth") {
                authRouter()
            }
        }

        stubMicrosoftTokenEndpoint()
        stubXboxAuthEndpoint()
        stubXstsAuthEndpoint()
        stubMinecraftTokenEndpoint()
        stubMinecraftProfileEndpoint()

        val response = client.get("/auth/oidc/microsoft-redirect?code=$microsoftCode&state=${state("/")}") {
            withNonceCookie(this)
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/", response.headers["Location"])
        val cookie = response.setCookie().find { it.name == AUTH_COOKIE }
        assertNotNull(cookie)
        assert(cookie.value.isNotEmpty()) { "Auth cookie should not be empty" }
    }

    @Test
    fun `Redirect to custom URL when sign in with redirect URL`() = testApplication {
        val client = createClient { followRedirects = false }

        val microsoftCode = "code123"

        routing {
            route("/auth") {
                authRouter()
            }
        }

        stubMicrosoftTokenEndpoint()
        stubXboxAuthEndpoint()
        stubXstsAuthEndpoint()
        stubMinecraftTokenEndpoint()
        stubMinecraftProfileEndpoint()

        val response = client.get("/auth/oidc/microsoft-redirect?code=$microsoftCode&state=${state("/custom-path")}") {
            withNonceCookie(this)
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/custom-path", response.headers["Location"])
    }

    @Test
    fun `Should redirect to sign-out on error`() = testApplication {
        val client = createClient { followRedirects = false }

        routing {
            route("/auth") {
                authRouter()
            }
        }

        // Carries a valid nonce so the callback gets past the MCO-355 state check and reaches the
        // missing-code branch this test is actually about.
        val response = client.get("/auth/oidc/microsoft-redirect?state=${state("/")}") {
            withNonceCookie(this)
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/auth/sign-out?error=missing_code", response.headers["Location"])
    }

    // --- MCO-355: login CSRF -----------------------------------------------------------------

    @Test
    fun `a callback with no state nonce is rejected before the code is exchanged`() = testApplication {
        val client = createClient { followRedirects = false }

        routing { route("/auth") { authRouter() } }

        stubMicrosoftTokenEndpoint()

        // The login-CSRF shape: the attacker sends the victim a callback URL carrying the
        // attacker's own authorization code. They cannot set a cookie on the victim's browser for
        // this origin, so the nonce half is missing.
        val response = client.get("/auth/oidc/microsoft-redirect?code=attacker-code&state=/")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/auth/sign-in?error=invalid_state", response.headers["Location"])
        assertNull(
            response.setCookie().find { it.name == AUTH_COOKIE },
            "a rejected callback must not issue a session cookie",
        )
        // Rejected *before* the exchange — the whole point, since the code is single-use.
        wireMock.verifyThat(0, WireMock.postRequestedFor(WireMock.urlEqualTo("/consumers/oauth2/v2.0/token")))
    }

    @Test
    fun `a callback whose nonce does not match the cookie is rejected`() = testApplication {
        val client = createClient { followRedirects = false }

        routing { route("/auth") { authRouter() } }

        stubMicrosoftTokenEndpoint()

        val response = client.get("/auth/oidc/microsoft-redirect?code=attacker-code&state=someone-elses-nonce:/") {
            withNonceCookie(this)
        }

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/auth/sign-in?error=invalid_state", response.headers["Location"])
        wireMock.verifyThat(0, WireMock.postRequestedFor(WireMock.urlEqualTo("/consumers/oauth2/v2.0/token")))
    }

    @Test
    fun `a malformed percent-escape in state is rejected, not thrown`() = testApplication {
        // This handler is reachable without a session — AuthPlugin exempts /oidc so the callback
        // can arrive before one exists — which makes every parse on this path internet-facing.
        //
        // `parameters` is already percent-decoded by Ktor, and a second URLDecoder.decode() pass
        // used to run before the nonce check and outside any try/catch. URLDecoder throws
        // IllegalArgumentException on a stray `%`, so `state=%25zz` — which decodes to the literal
        // `%zz` — produced a 500 and a stack trace from an unauthenticated GET anyone could put in
        // a loop.
        val client = createClient { followRedirects = false }

        routing { route("/auth") { authRouter() } }
        stubMicrosoftTokenEndpoint()

        listOf("%25zz", "%25", "abc%252").forEach { hostileState ->
            val response = client.get("/auth/oidc/microsoft-redirect?code=attacker-code&state=$hostileState")

            assertEquals(
                HttpStatusCode.Found, response.status,
                "state=$hostileState should be refused cleanly, not thrown on",
            )
            assertEquals("/auth/sign-in?error=invalid_state", response.headers["Location"])
        }

        // Still refused before the single-use code is spent.
        wireMock.verifyThat(0, WireMock.postRequestedFor(WireMock.urlEqualTo("/consumers/oauth2/v2.0/token")))
    }

    @Test
    fun `the sign-in page issues a nonce cookie and puts its twin in the state`() = testApplication {
        val client = createClient { followRedirects = false }

        routing { route("/auth") { authRouter() } }

        val response = client.get("/auth/sign-in")

        val nonceCookie = response.setCookie().find { it.name == OAUTH_STATE_COOKIE }
        assertNotNull(nonceCookie, "sign-in should issue the nonce cookie")
        assertTrue(nonceCookie.value.isNotBlank(), "the nonce should not be empty")
        assertTrue(nonceCookie.httpOnly, "the nonce cookie must not be readable from JavaScript")

        // The same value has to reach Microsoft in `state`, or the callback can never match it.
        val body = response.bodyAsText()
        assertTrue(
            body.contains(URLEncoder.encode("${nonceCookie.value}:", "UTF-8")),
            "the sign-in URL should carry the cookie's nonce in its state parameter",
        )
    }

    /** A fixed nonce for tests, paired with [withNonceCookie]. */
    private val testNonce = "test-nonce-value"

    private fun state(redirectPath: String) =
        URLEncoder.encode("$testNonce:$redirectPath", "UTF-8")

    private fun withNonceCookie(builder: HttpRequestBuilder) {
        builder.cookie(OAUTH_STATE_COOKIE, testNonce)
    }

    fun stubMicrosoftTokenEndpoint() {
        wireMock.register(WireMock.post("/consumers/oauth2/v2.0/token").willReturn(WireMock.aResponse().withStatus(200)
            // language=json
            .withBody("""
                {
                  "token_type": "Bearer",
                  "scope": "XboxLive.signin",
                  "expires_in": 3600,
                  "ext_expires_in": 3600,
                  "access_token": "EwB4A8l6BAAUo...",
                  "id_token": "..."
                }
            """.trimIndent())))
    }

    fun stubXboxAuthEndpoint() {
        wireMock.register(WireMock.post("/user/authenticate").willReturn(WireMock.aResponse().withBody(
            // language=json
            """
                {
                  "IssueInstant": "2024-10-01T12:34:56.789Z",
                  "NotAfter": "2024-10-01T13:34:56.789Z",
                  "Token": "xbox_token",
                  "DisplayClaims": {
                    "xui": [
                      {
                        "uhs": "1234567890abcdef"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )))
    }

    fun stubXstsAuthEndpoint() {
        wireMock.register(WireMock.post("/xsts/authorize").willReturn(WireMock.aResponse().withBody(
            // language=json
            """
                {
                  "IssueInstant": "2024-10-01T12:34:56.789Z",
                  "NotAfter": "2024-10-01T13:34:56.789Z",
                  "Token": "xsts_token",
                  "DisplayClaims": {
                    "xui": [
                      {
                        "uhs": "1234567890abcdef"
                      }
                    ]
                  }
                }
            """.trimIndent()
        )))
    }

    fun stubMinecraftTokenEndpoint() {
        wireMock.register(WireMock.post("/authentication/login_with_xbox").willReturn(WireMock.aResponse().withBody(
            // language=json
            """
                {
                  "username": "evegul",
                  "roles": [],
                  "metadata": {},
                  "access_token": "minecraft_token",
                  "expires_in": 86400,
                  "token_type": "Bearer"
                }
            """.trimIndent()
        )))
    }

    fun stubMinecraftProfileEndpoint() {
        wireMock.register(WireMock.get("/minecraft/profile").willReturn(WireMock.aResponse().withBody(
            // language=json
            """
                {
                  "id": "evegul-uuid",
                  "name": "evegul",
                  "skins": [],
                  "capes": []
                }
            """.trimIndent()
        )))
    }
}