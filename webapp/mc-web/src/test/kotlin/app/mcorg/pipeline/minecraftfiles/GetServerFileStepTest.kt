package app.mcorg.pipeline.minecraftfiles

import app.mcorg.config.OutboundHttp
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.github.tomakehurst.wiremock.stubbing.Scenario
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import java.io.InputStream
import java.net.URI
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The SHA-1 download verification, against a real socket: the step streams whatever bytes the
 * URL serves through the shared client, so WireMock on loopback exercises the exact code path a
 * Mojang download takes — including the client's retry and the headers it sends (MCO-552).
 *
 * These used to run over `file://` URIs, which the JDK's `URL.openConnection()` accepted and a
 * Ktor client does not.
 */
@WireMockTest
class GetServerFileStepTest {

    private val version = MinecraftVersion.Release(1, 21, 4)
    private val payload = "not a real server.jar, but the digest does not care".toByteArray()

    private lateinit var wireMock: WireMock
    private lateinit var baseUrl: String

    @BeforeEach
    fun setup(info: WireMockRuntimeInfo) {
        wireMock = info.wireMock
        baseUrl = info.httpBaseUrl
        wireMock.register(WireMock.get("/server.jar").willReturn(WireMock.aResponse().withStatus(200).withBody(payload)))
    }

    private fun sha1Of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun resolvedJar(sha1: String, path: String = "/server.jar"): ResolvedServerJar =
        ResolvedServerJar(version, URI.create(baseUrl + path), sha1)

    private fun downloadTempFiles(): Set<String> =
        Path.of(System.getProperty("java.io.tmpdir")).listDirectoryEntries()
            .map { it.name }
            .filter { it.startsWith("server-$version") }
            .toSet()

    @Test
    fun `accepts a download whose SHA-1 matches and serves the verified bytes`() = runBlocking {
        val result = GetServerFileStep.process(resolvedJar(sha1Of(payload)))

        val success = assertIs<Result.Success<Pair<MinecraftVersion.Release, InputStream>>>(result)
        val (resolvedVersion, stream) = success.value
        assertEquals(version, resolvedVersion)
        stream.use { assertContentEquals(payload, it.readAllBytes()) }
    }

    @Test
    fun `SHA-1 comparison is case-insensitive`() = runBlocking {
        val result = GetServerFileStep.process(resolvedJar(sha1Of(payload).uppercase()))

        val success = assertIs<Result.Success<Pair<MinecraftVersion.Release, InputStream>>>(result)
        success.value.second.use { assertContentEquals(payload, it.readAllBytes()) }
    }

    @Test
    fun `no temp file survives consuming and closing the stream`() = runBlocking {
        // On Linux DELETE_ON_CLOSE unlinks the file already at open (the fd stays readable),
        // on other platforms at close — either way nothing may remain afterwards.
        val before = downloadTempFiles()

        val result = GetServerFileStep.process(resolvedJar(sha1Of(payload)))
        val success = assertIs<Result.Success<Pair<MinecraftVersion.Release, InputStream>>>(result)
        success.value.second.use { it.readAllBytes() }

        assertTrue(
            (downloadTempFiles() - before).isEmpty(),
            "Expected no temp file to remain after the stream is consumed and closed"
        )
    }

    @Test
    fun `rejects a download whose SHA-1 does not match`() = runBlocking {
        val wrongSha = "0".repeat(40)
        val before = downloadTempFiles()

        val result = GetServerFileStep.process(resolvedJar(wrongSha))

        val failure = assertIs<Result.Failure<AppFailure>>(result)
        val mismatch = assertIs<AppFailure.ApiError.ChecksumMismatch>(failure.error)
        assertEquals(wrongSha, mismatch.expected)
        assertEquals(sha1Of(payload), mismatch.actual)

        assertTrue(
            (downloadTempFiles() - before).isEmpty(),
            "Expected no temp file to be left behind after a checksum mismatch"
        )
    }

    @Test
    fun `fails without leftover temp files when the download source does not exist`() = runBlocking {
        val before = downloadTempFiles()

        val result = GetServerFileStep.process(resolvedJar(sha1Of(payload), path = "/missing.jar"))

        val failure = assertIs<Result.Failure<AppFailure>>(result)
        val http = assertIs<AppFailure.ApiError.HttpError>(failure.error)
        assertEquals(404, http.statusCode)
        // A 4xx is "this request is wrong" and is never retried (MCO-354).
        wireMock.verifyThat(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/missing.jar")))
        assertTrue(
            (downloadTempFiles() - before).isEmpty(),
            "Expected no temp file to be left behind after a failed download"
        )
    }

    @Test
    fun `identifies itself as Seam`() = runBlocking {
        GetServerFileStep.process(resolvedJar(sha1Of(payload)))

        wireMock.verifyThat(
            WireMock.getRequestedFor(WireMock.urlEqualTo("/server.jar"))
                .withHeader("User-Agent", WireMock.equalTo(OutboundHttp.USER_AGENT))
        )
    }

    @Test
    fun `a CDN that answers 503 once is retried through the shared client`() = runBlocking {
        // Before MCO-552 the download had its own JDK connection and no retry, so one bad CDN
        // answer failed the version until the next nightly run.
        wireMock.register(
            WireMock.get("/flaky.jar").inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse().withStatus(503))
                .willSetStateTo("recovered")
        )
        wireMock.register(
            WireMock.get("/flaky.jar").inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(WireMock.aResponse().withStatus(200).withBody(payload))
        )

        val result = GetServerFileStep.process(resolvedJar(sha1Of(payload), path = "/flaky.jar"))

        val success = assertIs<Result.Success<Pair<MinecraftVersion.Release, InputStream>>>(result)
        success.value.second.use { assertContentEquals(payload, it.readAllBytes()) }
        wireMock.verifyThat(2, WireMock.getRequestedFor(WireMock.urlEqualTo("/flaky.jar")))
    }
}
