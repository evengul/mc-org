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
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

/**
 * Drains the `webhook_deliveries` outbox: each [pollOnce] picks up all due rows, groups them by
 * subscription, and delivers each subscription's batch concurrently as one signed POST. The poll
 * tick itself is the batching window — events that accrue between ticks for the same subscription
 * coalesce into a single payload (see [WebhookPayload]).
 *
 * Delivery outcome drives both the outbox row and subscription health:
 *  - success → rows marked `DELIVERED`, the subscription's failure streak reset;
 *  - failure → rows rescheduled with backoff (immediate → 30s → 5min) or marked `FAILED` after
 *    [MAX_ATTEMPTS]; the subscription's failure streak bumped, auto-deactivating it at
 *    [DEACTIVATE_THRESHOLD] consecutive failures.
 *
 * Started and stopped by `configureWebhooks` against the Ktor lifecycle. Stateless apart from the
 * shared [client] and the cleanup throttle, so it is safe to drive from a single polling loop.
 */
class WebhookDeliveryPoller(
    private val client: HttpClient = defaultClient(),
) {
    private val logger = LoggerFactory.getLogger(WebhookDeliveryPoller::class.java)

    @Volatile
    private var lastCleanupAtMs: Long = 0

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
        const val POLL_INTERVAL_MS = 5_000L
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
