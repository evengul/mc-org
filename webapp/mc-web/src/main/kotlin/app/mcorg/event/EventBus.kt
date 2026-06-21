package app.mcorg.event

/** A subscriber invoked once per published [SeamEvent]. */
fun interface EventHandler {
    suspend fun handle(event: SeamEvent)
}

/**
 * In-process publish/subscribe for [SeamEvent]s. Publishing is fire-and-forget: [publish] returns
 * immediately and fans out to every subscriber concurrently. A handler that throws is isolated —
 * it neither propagates to the caller nor affects sibling handlers.
 *
 * Subscriptions are expected to be registered once at startup; there is no unsubscribe.
 */
interface EventBus {
    /** Hand [event] off to all subscribers. Never blocks on or fails for handler work. */
    fun publish(event: SeamEvent)

    /** Register [handler] to receive all future events. */
    fun subscribe(handler: EventHandler)
}
