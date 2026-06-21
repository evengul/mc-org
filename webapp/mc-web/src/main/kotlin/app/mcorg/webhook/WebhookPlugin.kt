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
import kotlinx.coroutines.delay
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
 */
fun Application.configureWebhooks() {
    val logger = LoggerFactory.getLogger("WebhookPlugin")
    SeamEventBus.bus.subscribe(WebhookFanoutConsumer())

    monitor.subscribe(ApplicationStarted) {
        SeamWebhooks.scope.launch {
            while (isActive) {
                try {
                    SeamWebhooks.poller.pollOnce(System.currentTimeMillis())
                } catch (e: Exception) {
                    logger.error("Webhook poll cycle failed", e)
                }
                delay(WebhookDeliveryPoller.POLL_INTERVAL_MS)
            }
        }
    }
    monitor.subscribe(ApplicationStopping) { SeamWebhooks.scope.cancel("Application stopping") }
}
