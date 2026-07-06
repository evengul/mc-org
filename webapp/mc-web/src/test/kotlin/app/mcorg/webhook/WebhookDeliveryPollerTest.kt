package app.mcorg.webhook

import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the wake/park mechanism in [WebhookDeliveryPoller.awaitNextWake] and
 * [WebhookDeliveryPoller.signalWork] — the event-driven replacement for the old fixed 5s heartbeat
 * (MCO-251). [WebhookStore] is mocked throughout so these run without a database; the persisted
 * semantics of [WebhookStore.findNextScheduledDeliveryAt] itself are covered by the `database`-tagged
 * tests in [WebhookDeliveryIT].
 *
 * Timing notes: the `nowMs` fed into [WebhookDeliveryPoller.awaitNextWake] is always an explicit,
 * fixed value (never re-sampled), so the *delay it computes* is deterministic regardless of how much
 * wall-clock time actually elapses while the test sets up its mocks. Elapsed *test* time is measured
 * with [System.nanoTime] rather than [System.currentTimeMillis] so assertions can't be fooled by a
 * wall-clock adjustment (NTP, etc.) making a measurement look negative; bounds are kept generous to
 * tolerate CI/database-tier scheduling jitter.
 */
class WebhookDeliveryPollerTest {

    private val poller = WebhookDeliveryPoller()

    @BeforeEach
    fun setup() {
        mockkObject(WebhookStore)
        coEvery { WebhookStore.pruneOldDeliveries() } returns Unit
        coEvery { WebhookStore.findDueDeliveries(any()) } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(WebhookStore)
    }

    /**
     * Real usage (`configureWebhooks`) always calls [WebhookDeliveryPoller.pollOnce] once before the
     * first [WebhookDeliveryPoller.awaitNextWake] — and that first `pollOnce` is what establishes a
     * real-clock cleanup deadline. Seed that here so tests that exercise `awaitNextWake` in isolation
     * match the loop's actual calling contract instead of racing the poller's just-constructed state.
     */
    private suspend fun primeCleanupClock() = poller.pollOnce(System.currentTimeMillis())

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    @Test
    fun `signalWork wakes an already-parked awaitNextWake almost immediately`() = runBlocking {
        primeCleanupClock()
        // Nothing scheduled: absent a signal, awaitNextWake would otherwise wait out the (~24h)
        // cleanup deadline, so a fast wake here can only be explained by the signal.
        coEvery { WebhookStore.findNextScheduledDeliveryAt() } returns null

        val nowMs = System.currentTimeMillis()
        val startNanos = System.nanoTime()
        val wake = async { poller.awaitNextWake(nowMs) }
        delay(20) // let awaitNextWake start parking before we signal
        poller.signalWork()

        withTimeout(10_000) { wake.await() }
        val elapsed = elapsedMs(startNanos)
        assertTrue(elapsed < 5_000, "expected a wake far short of the ~24h cleanup deadline, took ${elapsed}ms")
    }

    @Test
    fun `a signal sent before awaitNextWake is called is not lost`() = runBlocking {
        primeCleanupClock()
        coEvery { WebhookStore.findNextScheduledDeliveryAt() } returns null

        // Simulates the race: the enqueue signal fires while pollOnce is still running, i.e.
        // strictly before the loop ever calls awaitNextWake.
        poller.signalWork()

        val nowMs = System.currentTimeMillis()
        val startNanos = System.nanoTime()
        withTimeout(10_000) { poller.awaitNextWake(nowMs) }
        val elapsed = elapsedMs(startNanos)
        assertTrue(elapsed < 5_000, "buffered signal should resolve well short of the cleanup deadline, took ${elapsed}ms")
    }

    @Test
    fun `awaits until the next scheduled retry when the outbox has future-due work`() = runBlocking {
        primeCleanupClock()
        // nowMs is fixed and fed straight into awaitNextWake, so the delay it computes
        // (scheduledDelayMs) does not depend on how long mock setup itself happens to take.
        val nowMs = System.currentTimeMillis()
        val scheduledDelayMs = 300L
        coEvery { WebhookStore.findNextScheduledDeliveryAt() } returns Instant.ofEpochMilli(nowMs + scheduledDelayMs)

        val startNanos = System.nanoTime()
        withTimeout(10_000) { poller.awaitNextWake(nowMs) }
        val elapsed = elapsedMs(startNanos)

        assertTrue(elapsed >= scheduledDelayMs - 50, "should not wake meaningfully before the scheduled retry, took only ${elapsed}ms")
        assertTrue(elapsed < scheduledDelayMs + 5_000, "should wake within a reasonable margin of the scheduled retry, took ${elapsed}ms")
    }

    @Test
    fun `idle loop with an empty outbox issues no further due-delivery polls until signalled`() = runBlocking {
        val dueCalls = AtomicInteger(0)
        coEvery { WebhookStore.findDueDeliveries(any()) } coAnswers {
            dueCalls.incrementAndGet()
            emptyList()
        }
        // Nothing scheduled and no cleanup due yet: this is the "fully idle" regression guard for
        // the Neon autosuspend fix -- the loop must park rather than spin.
        coEvery { WebhookStore.findNextScheduledDeliveryAt() } returns null

        val job = launch {
            while (isActive) {
                poller.pollOnce(System.currentTimeMillis())
                poller.awaitNextWake()
            }
        }

        // Let the loop run its first (unavoidable) pollOnce, then sit idle for a while.
        delay(300)
        assertEquals(1, dueCalls.get(), "idle loop must not re-poll on its own")

        poller.signalWork()
        withTimeout(10_000) {
            while (dueCalls.get() < 2) delay(10)
        }
        assertEquals(2, dueCalls.get(), "a signal must trigger exactly one more poll")

        job.cancel()
    }
}
