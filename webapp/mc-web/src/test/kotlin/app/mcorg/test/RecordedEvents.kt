package app.mcorg.test

import app.mcorg.event.SeamEvent
import app.mcorg.event.SeamEventBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Every [SeamEvent] the process-wide bus has carried since the first test touched this object.
 *
 * The bus has no unsubscribe, so the recorder is subscribed once for the whole JVM and is shared
 * by every test class. Filter by the ids your own test created; never count the whole list — other
 * classes publish too.
 */
object RecordedEvents {
    private val events = CopyOnWriteArrayList<SeamEvent>()

    init {
        SeamEventBus.bus.subscribe { events.add(it) }
    }

    /** Subscribes the recorder. Call before the request whose events you want to see. */
    fun start() = Unit

    /**
     * Events of type [T] that match [predicate], after every dispatch already launched has run.
     *
     * Publishing launches each handler on the bus's scope, so the HTTP response can arrive before
     * the recorder has seen the event. The handler is launched before `publish` returns, so joining
     * the scope's children is enough — no polling, and a missing event reads as zero, not a timeout.
     */
    inline fun <reified T : SeamEvent> of(crossinline predicate: (T) -> Boolean = { true }): List<T> {
        settle()
        return snapshot().filterIsInstance<T>().filter(predicate)
    }

    @PublishedApi
    internal fun snapshot(): List<SeamEvent> = events.toList()

    @PublishedApi
    internal fun settle() = runBlocking {
        // `configureEvents` cancels this scope when an application stops. A cancelled scope drops
        // every publish silently, which would read here as "nothing was published".
        check(SeamEventBus.scope.isActive) {
            "SeamEventBus.scope is cancelled — a test stopped an application that ran configureEvents"
        }
        withTimeout(5_000) {
            SeamEventBus.scope.coroutineContext[Job]!!.children.toList().joinAll()
        }
    }
}
