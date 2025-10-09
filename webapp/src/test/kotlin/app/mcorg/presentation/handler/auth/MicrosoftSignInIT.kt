package app.mcorg.presentation.handler.auth

import app.mcorg.config.AppConfig
import app.mcorg.domain.Production
import app.mcorg.presentation.consts.AUTH_COOKIE
import app.mcorg.presentation.router.authRouter
import app.mcorg.test.postgres.DatabaseTestExtension
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
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

@WireMockTest(proxyMode = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class MicrosoftSignInIT {
    @BeforeEach
    fun setup(wiremock: WireMockRuntimeInfo) {
        AppConfig.env = Production
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

        val response = client.get("/auth/oidc/microsoft-redirect?code=$microsoftCode")

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

        val response = client.get("/auth/oidc/microsoft-redirect?code=$microsoftCode&state=/custom-path")

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

        val response = client.get("/auth/oidc/microsoft-redirect")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/auth/sign-out?error=missing_code&args=", response.headers["Location"])
    }

    fun stubMicrosoftTokenEndpoint() {
        stubFor(WireMock.post("/consumers/oauth2/v2.0/token").willReturn(WireMock.aResponse().withStatus(200)
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
        stubFor(WireMock.post("/user/authenticate").willReturn(WireMock.aResponse().withBody(
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
        stubFor(WireMock.post("/xsts/authorize").willReturn(WireMock.aResponse().withBody(
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
        stubFor(WireMock.post("/authentication/login_with_xbox").willReturn(WireMock.aResponse().withBody(
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
        stubFor(WireMock.get("/minecraft/profile").willReturn(WireMock.aResponse().withBody(
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