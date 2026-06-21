package app.mcorg.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [EventBus] backed by a [CoroutineScope] tied to the application lifecycle (see
 * `Application.configureEvents`). The scope is built on a `SupervisorJob`, so one handler failing
 * never cancels the scope or sibling handlers; each dispatch is additionally wrapped so exceptions
 * are logged rather than lost. When the scope is cancelled at shutdown, in-flight handlers stop and
 * further publishes become no-ops.
 *
 * [subscribe] uses a copy-on-write list: subscriptions happen once at startup while publishes are
 * frequent and concurrent, so reads are lock-free.
 */
class InProcessEventBus(private val scope: CoroutineScope) : EventBus {
    private val logger = LoggerFactory.getLogger(InProcessEventBus::class.java)
    private val handlers = CopyOnWriteArrayList<EventHandler>()

    override fun subscribe(handler: EventHandler) {
        handlers.add(handler)
    }

    override fun publish(event: SeamEvent) {
        for (handler in handlers) {
            scope.launch {
                try {
                    handler.handle(event)
                } catch (e: Exception) {
                    logger.error("Event handler failed for {} ({})", event.eventType, event, e)
                }
            }
        }
    }
}
