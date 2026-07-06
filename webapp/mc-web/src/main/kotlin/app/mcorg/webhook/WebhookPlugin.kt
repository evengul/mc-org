package app.mcorg.webhook

import app.mcorg.event.SeamEventBus
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Process-wide webhook delivery, following the same singleton convention as [SeamEventBus]: a
 * lifecycle-bound coroutine scope (`SupervisorJob`, so one bad poll never tears the loop down) plus
 * the poller it drives.
 */
object SeamWebhooks {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("seam-webhook-delivery"))
    val poller = WebhookDeliveryPoller()
}

/**
 * Subscribes the [WebhookFanoutConsumer] to the event bus and starts the outbox polling loop, tying
 * both to the Ktor lifecycle. The loop starts on `ApplicationStarted` (so it does not poll a
 * not-yet-ready app) and the scope is cancelled on `ApplicationStopping`. Call once from
 * `Application.module()`, after `configureEvents()`.
 *
 * The loop is wake-on-demand rather than a fixed heartbeat: [WebhookFanoutConsumer] signals
 * [SeamWebhooks.poller] right after it enqueues an outbox row, and between poll cycles the loop
 * parks on [WebhookDeliveryPoller.awaitNextWake] — which resolves on that signal, the next
 * scheduled retry, or the cleanup throttle window elapsing, whichever comes first. An idle app
 * therefore issues no `webhook_deliveries` queries, letting the database autosuspend.
 */
fun Application.configureWebhooks() {
    val logger = LoggerFactory.getLogger("WebhookPlugin")
    SeamEventBus.bus.subscribe(WebhookFanoutConsumer(onEnqueued = SeamWebhooks.poller::signalWork))

    monitor.subscribe(ApplicationStarted) {
        SeamWebhooks.scope.launch {
            while (isActive) {
                try {
                    SeamWebhooks.poller.pollOnce(System.currentTimeMillis())
                } catch (e: Exception) {
                    logger.error("Webhook poll cycle failed", e)
                }
                SeamWebhooks.poller.awaitNextWake()
            }
        }
    }
    monitor.subscribe(ApplicationStopping) { SeamWebhooks.scope.cancel("Application stopping") }
}
