package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.nbt.failure.NBTFailure
import app.mcorg.pipeline.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * MCO-426: every schematic parse goes through one gate that bounds how many run at once and how
 * long each may take. The reader is stubbed, so these are about the gate and run in milliseconds;
 * that the real reader stops when interrupted is mc-nbt's ParseInterruptionTest.
 */
class SchematicParseGateTest {

    private val litematica = Litematica(
        name = "stub",
        author = "test",
        description = "",
        size = Triple(1, 1, 1),
        items = mapOf("minecraft:stone" to 1),
    )

    private val parsed: (ByteArray) -> Result<NBTFailure, Litematica> = { Result.success(litematica) }

    private val oneFile = listOf(ByteArray(1))

    /** A reader that blocks until interrupted, recording that it was. */
    private class Stuck {
        val started = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)

        val read: (ByteArray) -> Result<NBTFailure, Litematica> = {
            started.countDown()
            try {
                Thread.sleep(60_000)
                error("a stuck parse should have been interrupted")
            } catch (e: InterruptedException) {
                interrupted.set(true)
                throw e
            }
        }
    }

    @Test
    fun `a readable file comes back parsed`() = runBlocking<Unit> {
        val gate = SchematicParseGate(concurrentParses = 1, timeout = 5.seconds, read = parsed)

        val result = gate.parse(oneFile)

        assertIs<Result.Success<List<Litematica>>>(result)
        assertEquals(listOf(litematica), result.value)
    }

    @Test
    fun `an unreadable file is reported as unreadable`() = runBlocking<Unit> {
        val gate = SchematicParseGate(1, 5.seconds, read = { Result.failure(NBTFailure.InvalidStructure) })

        val result = gate.parse(oneFile)

        assertIs<Result.Failure<FileParseFailure>>(result)
        assertEquals(FileParseFailure(0, SchematicParseFailure.Unreadable), result.error)
    }

    @Test
    fun `a parse past the timeout is interrupted and reported as timed out`() = runBlocking<Unit> {
        val stuck = Stuck()
        val gate = SchematicParseGate(1, 200.milliseconds, read = stuck.read)

        val started = System.nanoTime()
        val result = gate.parse(oneFile)
        val elapsed = (System.nanoTime() - started) / 1_000_000

        assertIs<Result.Failure<FileParseFailure>>(result)
        assertEquals(FileParseFailure(0, SchematicParseFailure.TimedOut), result.error)
        // Interrupted, not abandoned: a timeout that let the thread run on would free the request
        // and leave the IO thread and the permit pinned to a parse nobody is waiting for.
        assertTrue(stuck.interrupted.get(), "the parse thread should have been interrupted")
        assertTrue(elapsed < 5_000, "the timeout should return promptly; took $elapsed ms")
    }

    @Test
    fun `a caller that goes away interrupts the parse and frees its permit`() = runBlocking<Unit> {
        // A client disconnect cancels the request's coroutine; the parse must stop with it.
        val stuck = Stuck()
        val calls = AtomicInteger(0)
        val gate = SchematicParseGate(1, 60.seconds, read = { bytes ->
            if (calls.incrementAndGet() == 1) stuck.read(bytes) else Result.success(litematica)
        })

        // Off runBlocking's one thread, which the latch below blocks.
        val request = launch(Dispatchers.Default) { gate.parse(oneFile) }
        assertTrue(stuck.started.await(5, TimeUnit.SECONDS), "the parse should have started")
        request.cancel()
        request.join()

        assertTrue(stuck.interrupted.get(), "cancelling the caller should interrupt the parse")
        // The gate has one permit, so this would wait out the 60 s timeout if the cancelled parse
        // had kept it.
        val next = withTimeoutOrNull(5.seconds) { gate.parse(oneFile) }
        assertIs<Result.Success<List<Litematica>>>(next, "the cancelled parse should have released its permit")
    }

    @Test
    fun `no more parses run at once than the gate permits`() = runBlocking<Unit> {
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val gate = SchematicParseGate(2, 30.seconds, read = {
            peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            Thread.sleep(100)
            active.decrementAndGet()
            Result.success(litematica)
        })

        val results = coroutineScope {
            (1..6).map { async { gate.parse(oneFile) } }.awaitAll()
        }

        assertTrue(results.all { it is Result.Success }, "every parse should complete: $results")
        assertEquals(2, peak.get(), "parses should run two at a time, no more and no fewer")
    }

    @Test
    fun `one deadline covers every file of an upload`() = runBlocking<Unit> {
        // An upload may carry several files, and they parse one after another. A timeout per file
        // would let one request hold its upload in memory for files x timeout.
        val gate = SchematicParseGate(1, 400.milliseconds, read = {
            Thread.sleep(150)
            Result.success(litematica)
        })

        val result = gate.parse(List(3) { ByteArray(1) })

        assertIs<Result.Failure<FileParseFailure>>(result)
        assertEquals(FileParseFailure(2, SchematicParseFailure.TimedOut), result.error)
    }

    @Test
    fun `a failure says which file of the upload it was`() = runBlocking<Unit> {
        val gate = SchematicParseGate(1, 5.seconds, read = { bytes ->
            if (bytes[0] == 1.toByte()) Result.failure(NBTFailure.InvalidStructure) else Result.success(litematica)
        })

        val result = gate.parse(listOf(byteArrayOf(0), byteArrayOf(1), byteArrayOf(0)))

        assertIs<Result.Failure<FileParseFailure>>(result)
        assertEquals(FileParseFailure(1, SchematicParseFailure.Unreadable), result.error)
    }

    @Test
    fun `each failure names the file in what it tells the user`() {
        assertEquals("Could not read farm.litematic", SchematicParseFailure.Unreadable.describe("farm.litematic"))
        assertTrue("farm.litematic" in SchematicParseFailure.TimedOut.describe("farm.litematic"))
        assertTrue("too long" in SchematicParseFailure.TimedOut.describe("farm.litematic"))
    }
}
