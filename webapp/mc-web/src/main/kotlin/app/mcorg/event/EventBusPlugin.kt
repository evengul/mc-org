package app.mcorg.event

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Process-wide event bus. Following the codebase convention for shared infrastructure
 * (`CacheManager`, `Database`), the bus is a singleton reachable from any handler via [eventBus]
 * rather than threaded through DI — so it is available in unit/integration tests that bootstrap
 * routes directly without running the full application module.
 *
 * The backing coroutine scope uses a `SupervisorJob` (one failed handler never cancels the scope or
 * its siblings). In production [configureEvents] subscribes the [DerivedEventConsumer] once and
 * cancels the scope on `ApplicationStopping`, tying the bus to the Ktor lifecycle. In tests no
 * consumer is subscribed and publishes are harmless no-ops.
 */
object SeamEventBus {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("seam-event-bus"))
    val bus = InProcessEventBus(scope)
}

/**
 * Wires the [DerivedEventConsumer] and ties the bus's scope to the Ktor lifecycle (scope cancelled
 * on `ApplicationStopping`). Call once from `Application.module()`.
 */
fun Application.configureEvents() {
    SeamEventBus.bus.subscribe(DerivedEventConsumer(SeamEventBus.bus))
    monitor.subscribe(ApplicationStopping) { SeamEventBus.scope.cancel("Application stopping") }
}

/** The process-wide [EventBus]. */
val Application.eventBus: EventBus
    get() = SeamEventBus.bus

/** The process-wide [EventBus] for the current call. */
val ApplicationCall.eventBus: EventBus
    get() = SeamEventBus.bus
