package app.mcorg.webhook

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * Drains the `webhook_deliveries` outbox: each [pollOnce] picks up all due rows, groups them by
 * subscription, and delivers each subscription's batch concurrently as one signed POST. Whatever
 * span the caller's loop leaves between two [pollOnce] calls is the batching window — events that
 * accrue in that span for the same subscription coalesce into a single payload (see
 * [WebhookPayload]).
 *
 * Delivery outcome drives both the outbox row and subscription health:
 *  - success → rows marked `DELIVERED`, the subscription's failure streak reset;
 *  - failure → rows rescheduled with backoff (immediate → 30s → 5min) or marked `FAILED` after
 *    [MAX_ATTEMPTS]; the subscription's failure streak bumped, auto-deactivating it at
 *    [DEACTIVATE_THRESHOLD] consecutive failures.
 *
 * There is no fixed heartbeat: [awaitNextWake] is the wake/park mechanism the driving loop
 * (`configureWebhooks`) uses between calls to [pollOnce], so an idle app issues zero
 * `webhook_deliveries` queries and can autosuspend. Started and stopped by `configureWebhooks`
 * against the Ktor lifecycle. Stateless apart from the shared [client] and the cleanup throttle, so
 * it is safe to drive from a single polling loop.
 */
class WebhookDeliveryPoller(
    private val client: HttpClient = defaultClient(),
) {
    private val logger = LoggerFactory.getLogger(WebhookDeliveryPoller::class.java)

    // Seeded one full cleanup window in the past (rather than epoch 0) so both `pollOnce` (cleanup
    // always runs on the very first tick after (re)start) and `awaitNextWake` (a sane, real-clock
    // cleanup deadline even if called before any `pollOnce`) agree on "now" as their reference point.
    @Volatile
    private var lastCleanupAtMs: Long = System.currentTimeMillis() - CLEANUP_INTERVAL_MS

    /**
     * Conflated: at most one pending "wake up" is ever buffered, and a [signalWork] that fires
     * while [pollOnce] is still running (or before anyone is awaiting) is not lost — the next
     * [awaitNextWake] call observes it immediately instead of suspending.
     */
    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    /**
     * Wake the poll loop promptly. Called by [WebhookFanoutConsumer] right after it enqueues an
     * outbox row, so an active subscription is delivered to without waiting for a scheduled wake.
     * Never suspends — safe to call from any coroutine.
     */
    fun signalWork() {
        wakeSignal.trySend(Unit)
    }

    /** One poll cycle: deliver every due batch, then run cleanup if the throttle window elapsed. */
    suspend fun pollOnce(nowMs: Long) {
        val due = WebhookStore.findDueDeliveries()
        if (due.isNotEmpty()) {
            coroutineScope {
                due.groupBy { it.subscriptionId }
                    .map { (subscriptionId, rows) -> async { deliverBatch(subscriptionId, rows) } }
                    .awaitAll()
            }
        }
        if (nowMs - lastCleanupAtMs >= CLEANUP_INTERVAL_MS) {
            lastCleanupAtMs = nowMs
            WebhookStore.pruneOldDeliveries()
        }
    }

    /**
     * Wait for the next reason to run [pollOnce] again: whichever comes first of
     *  - an enqueue [signalWork] wake,
     *  - the earliest scheduled retry ([WebhookStore.findNextScheduledDeliveryAt]), or
     *  - the cleanup throttle window elapsing (so `pruneOldDeliveries` still runs on a long
     *    interval even while otherwise idle — this is a once-an-hour wake, not a heartbeat).
     *
     * When the outbox holds no pending row at all, this parks on [wakeSignal] for up to
     * [CLEANUP_INTERVAL_MS] and issues no `webhook_deliveries` query in the meantime, letting the
     * database autosuspend.
     */
    suspend fun awaitNextWake(nowMs: Long = System.currentTimeMillis()) {
        val nextScheduledAtMs = WebhookStore.findNextScheduledDeliveryAt()?.toEpochMilli()
        val nextCleanupAtMs = lastCleanupAtMs + CLEANUP_INTERVAL_MS
        val wakeAtMs = if (nextScheduledAtMs != null) minOf(nextScheduledAtMs, nextCleanupAtMs) else nextCleanupAtMs
        val delayMs = (wakeAtMs - nowMs).coerceAtLeast(0)
        withTimeoutOrNull(delayMs) { wakeSignal.receive() }
    }

    private suspend fun deliverBatch(subscriptionId: Int, rows: List<DueDelivery>) {
        val ids = rows.map { it.id }
        val body = WebhookPayload.build(rows.map { it.payload })
        val signature = WebhookSigner.sign(rows.first().secret, body)
        val error = post(rows.first().callbackUrl, body, signature)
        if (error == null) {
            WebhookStore.markDelivered(ids)
            WebhookStore.recordSubscriptionSuccess(subscriptionId)
        } else {
            logger.warn("Webhook delivery to subscription {} failed: {}", subscriptionId, error)
            WebhookStore.failOrReschedule(ids, MAX_ATTEMPTS, error)
            WebhookStore.recordSubscriptionFailure(subscriptionId, DEACTIVATE_THRESHOLD)
        }
    }

    /** POST the signed body. Returns `null` on a 2xx response, or a short error string otherwise. */
    private suspend fun post(url: String, body: String, signature: String): String? = try {
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            header(WebhookSigner.HEADER, signature)
            setBody(body)
        }
        if (response.status.isSuccess()) null else "HTTP ${response.status.value}"
    } catch (e: Exception) {
        e.message ?: e::class.simpleName ?: "delivery error"
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val DEACTIVATE_THRESHOLD = 10
        const val CLEANUP_INTERVAL_MS = 3_600_000L // 1 hour
        private const val REQUEST_TIMEOUT_MS = 5_000L

        private fun defaultClient() = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }
    }
}
