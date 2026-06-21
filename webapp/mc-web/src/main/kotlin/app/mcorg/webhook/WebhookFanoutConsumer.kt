package app.mcorg.webhook

import app.mcorg.event.EventEnvelope
import app.mcorg.event.EventHandler
import app.mcorg.event.SeamEvent

/**
 * Event-bus subscriber that fans a [SeamEvent] out to every active webhook subscription in the
 * event's world whose filter matches ([eventMatchesFilter]). It does no HTTP: it serializes the
 * envelope once and persists one outbox row per matching subscription. The [WebhookDeliveryPoller]
 * picks those up and performs the actual signed delivery, retry, and batching.
 *
 * Producers stay unaware of subscribers — this consumer is the only place subscriptions are matched.
 */
class WebhookFanoutConsumer : EventHandler {
    override suspend fun handle(event: SeamEvent) {
        val matching = WebhookStore.findActiveSubscriptions(event.worldId)
            .filter { eventMatchesFilter(it.eventFilter, event.eventType) }
        if (matching.isEmpty()) return

        val payload = EventEnvelope.serialize(event)
        matching.forEach { subscription ->
            WebhookStore.enqueueDelivery(subscription.id, event.eventType, payload)
        }
    }
}
