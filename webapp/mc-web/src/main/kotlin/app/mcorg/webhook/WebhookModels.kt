package app.mcorg.webhook

/**
 * A registered consumer of [app.mcorg.event.SeamEvent]s for one world. Fan-out matches an event
 * against every active subscription in its world (see [eventMatchesFilter]) and enqueues a
 * [WebhookDelivery] per match. The seam-discord bot is the first such consumer; the contract is
 * endpoint-agnostic.
 */
data class WebhookSubscription(
    val id: Int,
    val worldId: Int,
    val callbackUrl: String,
    val secret: String,
    val eventFilter: List<String>,
    val active: Boolean,
    val consecutiveFailures: Int,
)

/** Lifecycle of an outbox row, mirrored by the `status` CHECK constraint on `webhook_deliveries`. */
enum class DeliveryStatus { PENDING, DELIVERED, FAILED }

/**
 * A single outbox row that the [WebhookDeliveryPoller] has picked up because it is due, joined with
 * its subscription's routing data ([callbackUrl], [secret]). [payload] is the already-serialized
 * event envelope; the poller batches all due rows for one subscription into a single signed POST.
 */
data class DueDelivery(
    val id: Long,
    val subscriptionId: Int,
    val callbackUrl: String,
    val secret: String,
    val eventType: String,
    val payload: String,
    val attempts: Int,
)

/** Wildcard filter value that matches every event type. */
const val WILDCARD_EVENT_FILTER = "*"

/**
 * Whether an event of [eventType] should be delivered to a subscription whose `event_filter` is
 * [filter]. A filter containing [WILDCARD_EVENT_FILTER] matches everything; otherwise the event
 * type must be listed explicitly. An empty filter matches nothing.
 */
fun eventMatchesFilter(filter: List<String>, eventType: String): Boolean =
    filter.contains(WILDCARD_EVENT_FILTER) || filter.contains(eventType)
