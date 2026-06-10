package app.mcorg.pipeline.minecraftfiles

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.io.InputStream
import java.nio.file.Files
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
 * Hermetic tests for the SHA-1 download verification: the step hashes whatever bytes the URI
 * serves, so a file:// URI over a local payload exercises the exact same code path as a real
 * Mojang download.
 */
class GetServerFileStepTest {

    @TempDir
    lateinit var tempDir: Path

    private val version = MinecraftVersion.Release(1, 21, 4)
    private val payload = "not a real server.jar, but the digest does not care".toByteArray()

    private fun sha1Of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun resolvedJar(sha1: String): ResolvedServerJar {
        val source = tempDir.resolve("source.jar")
        Files.write(source, payload)
        return ResolvedServerJar(version, source.toUri(), sha1)
    }

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
        val missing = ResolvedServerJar(version, tempDir.resolve("missing.jar").toUri(), sha1Of(payload))

        val result = GetServerFileStep.process(missing)

        assertIs<Result.Failure<AppFailure>>(result)
        assertTrue(
            (downloadTempFiles() - before).isEmpty(),
            "Expected no temp file to be left behind after a failed download"
        )
    }
}
