package app.mcorg.pipeline.minecraftfiles

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The download's two timeout branches (MCO-346).
 *
 * Before this, `URL.openStream()` ran with `connectTimeout = readTimeout = 0` — infinite — inside
 * the ingestion advisory lock. A Mojang CDN connection that stalled did not fail the run; it
 * parked the Fly machine forever holding lock 7331, after which every nightly run logged
 * "another ingestion run is in progress, skipping" and exited 0. An indefinite outage that
 * reported success.
 *
 * Both cases here serve real HTTP off a local socket, so the failure is produced by the socket
 * and the coroutine rather than by a mock asserting its own premise.
 */
class GetServerFileTimeoutTest {

    private val version = MinecraftVersion.Release(1, 21, 4)
    private var server: ServerSocket? = null

    @AfterEach
    fun stopServer() {
        server?.close()
    }

    /**
     * Serves a response that promises a megabyte and then behaves according to [afterHeaders].
     * Bound to loopback so nothing here is reachable off the machine.
     */
    private fun serve(afterHeaders: (java.io.OutputStream) -> Unit): URI {
        val socket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        server = socket

        thread(isDaemon = true, name = "stalling-cdn") {
            try {
                socket.accept().use { client ->
                    // Drain the request line and headers so the client sees a well-formed exchange.
                    val input = client.getInputStream().bufferedReader()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val out = client.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\n\r\n".toByteArray())
                    out.flush()
                    afterHeaders(out)
                }
            } catch (_: Exception) {
                // The client hanging up is the expected end of every case here.
            }
        }

        return URI("http://${socket.inetAddress.hostAddress}:${socket.localPort}/server.jar")
    }

    private fun jarAt(url: URI) = ResolvedServerJar(version, url, "0".repeat(40))

    @Test
    fun `a connection that goes quiet mid-transfer fails on the read timeout`() {
        // Headers, then silence — the shape of the stall that hung the ingest machine. The
        // socket-level read timeout is the only thing that can break a blocked read.
        val url = serve { Thread.sleep(30_000) }

        val result = runBlocking {
            GetServerFileStep.download(jarAt(url), DownloadTimeouts(readMs = 250, wallClockMs = 30_000))
        }

        val failure = assertIs<Result.Failure<AppFailure>>(result)
        assertEquals(AppFailure.ApiError.TimeoutError, failure.error)
    }

    @Test
    fun `a connection that dribbles bytes forever fails on the wall clock`() {
        // Slow enough to never finish, fast enough that no single read times out. Only a total
        // ceiling catches this, which is why the copy loop checks for cancellation per chunk
        // instead of handing the whole transfer to Files.copy.
        val url = serve { out ->
            repeat(30_000) {
                out.write(byteArrayOf(0))
                out.flush()
                Thread.sleep(20)
            }
        }

        val result = runBlocking {
            GetServerFileStep.download(jarAt(url), DownloadTimeouts(readMs = 5_000, wallClockMs = 400))
        }

        val failure = assertIs<Result.Failure<AppFailure>>(result)
        assertEquals(AppFailure.ApiError.TimeoutError, failure.error)
    }

    @Test
    fun `a timeout leaves no temp file behind`() {
        val url = serve { Thread.sleep(30_000) }
        val tempDir = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"))
        val before = tempDir.toFile().list().orEmpty().filter { it.startsWith("server-$version") }.toSet()

        runBlocking {
            GetServerFileStep.download(jarAt(url), DownloadTimeouts(readMs = 250, wallClockMs = 30_000))
        }

        val after = tempDir.toFile().list().orEmpty().filter { it.startsWith("server-$version") }.toSet()
        assertEquals(emptySet(), after - before, "a timed-out download must not leave its temp file")
    }
}
