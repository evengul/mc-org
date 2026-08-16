package app.mcorg.config

import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.ktor.http.ContentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * MCO-354 — bounded retry on outbound HTTP.
 *
 * Nothing was retried before this: the sign-in chain is five sequential calls across four
 * providers, and any one of them returning 502 ended the flow at `?error=external_api_error`.
 *
 * The delicate part is scope, not mechanism. Retrying everything would replay the Microsoft token
 * exchange, which spends a single-use authorization `code` — turning a recoverable blip into a
 * guaranteed failure. These tests pin both halves: that transient failures are retried, and that
 * the things which must not be retried are not.
 */
@WireMockTest
class ApiRetryIT {

    @Serializable
    data class Payload(val value: String)

    private lateinit var wireMock: WireMock
    private lateinit var baseUrl: String

    @BeforeEach
    fun setup(info: WireMockRuntimeInfo) {
        wireMock = info.wireMock
        baseUrl = info.httpBaseUrl
    }

    /** The real provider against a real socket — the retry lives on the shared client, so a fake would prove nothing. */
    private fun provider() = DefaultApiProvider(TestApiConfig(baseUrl))

    private val okBody = """{"value":"ok"}"""

    @Test
    fun `a GET that returns 503 then 200 succeeds`() {
        wireMock.register(
            WireMock.get("/flaky").inScenario("flaky")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(503))
                .willSetStateTo("recovered")
        )
        wireMock.register(
            WireMock.get("/flaky").inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(WireMock.aResponse().withStatus(200).withBody(okBody))
        )

        val result = runBlocking {
            provider().get<Unit, Payload>("$baseUrl/flaky").process(Unit)
        }

        val success = assertIs<Result.Success<Payload>>(result)
        assertEquals("ok", success.value.value)
        wireMock.verifyThat(2, WireMock.getRequestedFor(WireMock.urlEqualTo("/flaky")))
    }

    @Test
    fun `a GET that returns 400 is not retried`() {
        // Every 4xx means "this request is wrong", and it will be exactly as wrong next time.
        wireMock.register(WireMock.get("/bad").willReturn(WireMock.aResponse().withStatus(400)))

        val result = runBlocking {
            provider().get<Unit, Payload>("$baseUrl/bad").process(Unit)
        }

        assertIs<Result.Failure<AppFailure.ApiError>>(result)
        wireMock.verifyThat(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/bad")))
    }

    @Test
    fun `a POST is not retried by default`() {
        // The property that protects the Microsoft token exchange. If this ever flips to retrying
        // by default, that call starts replaying a spent authorization code.
        wireMock.register(WireMock.post("/exchange").willReturn(WireMock.aResponse().withStatus(503)))

        val result = runBlocking {
            provider().post<Unit, Payload>("$baseUrl/exchange").process(Unit)
        }

        assertIs<Result.Failure<AppFailure.ApiError>>(result)
        wireMock.verifyThat(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/exchange")))
    }

    @Test
    fun `a POST marked retrySafe is retried`() {
        // What the Xbox, XSTS and Minecraft token calls opt into: they re-present a token already
        // held rather than spending anything.
        wireMock.register(
            WireMock.post("/handshake").inScenario("handshake")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(502))
                .willSetStateTo("recovered")
        )
        wireMock.register(
            WireMock.post("/handshake").inScenario("handshake")
                .whenScenarioStateIs("recovered")
                .willReturn(WireMock.aResponse().withStatus(200).withBody(okBody))
        )

        val result = runBlocking {
            provider().post<Unit, Payload>("$baseUrl/handshake", retrySafe = true).process(Unit)
        }

        assertIs<Result.Success<Payload>>(result)
        wireMock.verifyThat(2, WireMock.postRequestedFor(WireMock.urlEqualTo("/handshake")))
    }

    @Test
    fun `retries are bounded and the whole thing stays inside the request timeout`() {
        // An upstream that is down rather than flaky must not multiply into a long wait: three
        // attempts total, and the backoff has to fit inside the existing 30s budget.
        wireMock.register(WireMock.get("/down").willReturn(WireMock.aResponse().withStatus(503)))

        val startedAt = System.nanoTime()
        val result = runBlocking {
            provider().get<Unit, Payload>("$baseUrl/down").process(Unit)
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertIs<Result.Failure<AppFailure.ApiError>>(result)
        wireMock.verifyThat(3, WireMock.getRequestedFor(WireMock.urlEqualTo("/down")))
        assertTrue(elapsedMs < 30_000, "an all-failing upstream took ${elapsedMs}ms; must stay bounded")
    }
}
