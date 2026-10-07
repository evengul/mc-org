package app.mcorg.presentation.plugins

import app.mcorg.api.respondApiError
import app.mcorg.presentation.handler.respondRefusal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.util.AttributeKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Every per-client request limit the app enforces (MCO-274), in one place. The strategy — why these
 * routes, why in the app rather than at Cloudflare, why per address — is
 * documentation/rate-limiting.md.
 *
 * Each is a fixed window per client address ([clientAddress]): [limit] requests, then 429 with
 * `Retry-After` until [period] has passed since the window opened. Nested limits all apply, and
 * all count the request.
 */
enum class SeamRateLimit(val limit: Int, val period: Duration) {
    /**
     * Unauthenticated, and every call inserts a `device_code` row. A player links once; a
     * mistyped code or a restarted game is a handful more. Burst and sustained together.
     */
    DEVICE_CODE_CREATE_BURST(limit = 5, period = 1.minutes),
    DEVICE_CODE_CREATE_SUSTAINED(limit = 20, period = 1.hours),

    /**
     * Unauthenticated and a database read each. The per-code `slow_down` already holds one code
     * to its 5 s interval (12 a minute); this bounds a client spraying many codes.
     */
    DEVICE_CODE_POLL(limit = 30, period = 1.minutes),

    /**
     * The sign-in page and the Microsoft callback. A real sign-in is two or three requests; each
     * bogus callback costs an outbound round trip to Microsoft.
     */
    SIGN_IN(limit = 20, period = 1.minutes),

    /**
     * All of `/api/v1`, on top of the above. The mod's storage poll is one call per player every
     * 10 s, so a household of players behind one address stays far below this.
     */
    API(limit = 120, period = 1.minutes),
}

/**
 * Applies [limit] to every route [build] declares.
 *
 * A route-scoped plugin rather than Ktor's `RateLimit`, because of when it runs. `RateLimit`
 * checks its bucket after the route's own plugins, so a request [app.mcorg.api.ApiBearerAuthPlugin]
 * refuses — an invalid token, after a database lookup — never spends one, and a client spraying
 * bad tokens is never limited. This runs in the same phase as those plugins and, declared on a
 * parent route, ahead of them.
 */
fun Route.rateLimited(limit: SeamRateLimit, build: Route.() -> Unit): Route {
    // Its own child per limit: two siblings sharing one selector would share one node, and each
    // would pick up the other's limit.
    val limited = createChild(RateLimitSelector(limit))
    limited.install(rateLimitPlugins.getValue(limit))
    limited.build()
    return limited
}

private class RateLimitSelector(val limit: SeamRateLimit) : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Transparent

    override fun equals(other: Any?) = other is RateLimitSelector && other.limit == limit
    override fun hashCode() = limit.hashCode()
    override fun toString() = "(rate limit ${limit.name})"
}

private val WindowsKey = AttributeKey<FixedWindows>("SeamRateLimitWindows")

/** One plugin per limit: plugins are keyed by name, so a parent's and a child's both run. */
private val rateLimitPlugins: Map<SeamRateLimit, RouteScopedPlugin<Unit>> =
    SeamRateLimit.entries.associateWith { limit ->
        createRouteScopedPlugin("RateLimit-${limit.name}") {
            // Per application, so every test application starts with empty windows.
            val windows = application.attributes.computeIfAbsent(WindowsKey) { FixedWindows() }
            onUnansweredCall { call ->
                val waitSeconds = windows.tryAcquire(limit, call.clientAddress()) ?: return@onUnansweredCall
                call.response.header(HttpHeaders.RetryAfter, waitSeconds)
                call.respondTooManyRequests(waitSeconds)
            }
        }
    }

/**
 * Fixed-window counters, one per (limit, client). In memory, which is only correct because
 * production is one machine — a second would give every client a second allowance.
 */
internal class FixedWindows(private val clock: () -> Long = System::currentTimeMillis) {

    private class Window(val opensAtMillis: Long) {
        val used = AtomicInteger(0)
    }

    private val windows = ConcurrentHashMap<Pair<SeamRateLimit, String>, Window>()
    private val lastSweepMillis = AtomicLong(Long.MIN_VALUE)

    /** How many sweeps have run; read by tests to pin that sweeping is throttled. */
    internal val sweeps = AtomicInteger(0)

    /** Null when the call may proceed; otherwise the whole seconds until its window reopens. */
    fun tryAcquire(limit: SeamRateLimit, key: String): Long? {
        val now = clock()
        val period = limit.period.inWholeMilliseconds
        if (windows.size > SWEEP_THRESHOLD) sweepIfDue(now)
        val window = windows.compute(limit to key) { _, current ->
            if (current == null || now - current.opensAtMillis >= period) Window(now) else current
        }!!
        if (window.used.incrementAndGet() <= limit.limit) return null
        val remainingMillis = window.opensAtMillis + period - now
        return (remainingMillis + 999) / 1000
    }

    /** Number of windows held; a closed window is dropped on the next sweep. */
    val size: Int get() = windows.size

    /**
     * At most one sweep per [SWEEP_INTERVAL_MILLIS]. Without that, a map held over the threshold by
     * windows that are still open — an hour-long one, or many clients at once — would be walked in
     * full on every request, and the limiter would become the load it exists to prevent.
     */
    private fun sweepIfDue(now: Long) {
        val last = lastSweepMillis.get()
        if (last != Long.MIN_VALUE && now - last < SWEEP_INTERVAL_MILLIS) return
        if (lastSweepMillis.compareAndSet(last, now)) sweep(now)
    }

    /** Drops every closed window. A closed window and a missing one mean the same thing. */
    internal fun sweep(now: Long = clock()) {
        sweeps.incrementAndGet()
        windows.entries.removeIf { (key, window) -> now - window.opensAtMillis >= key.first.period.inWholeMilliseconds }
    }

    internal companion object {
        /** Sweeping is O(windows), so it waits until there are enough to be worth reclaiming. */
        const val SWEEP_THRESHOLD = 10_000
        const val SWEEP_INTERVAL_MILLIS = 60_000L
    }
}

/**
 * The body of a rate-limited response, in the caller's own terms.
 *
 * `/api/` gets the RFC 8628 `slow_down` error. That is the code the mod's device-code poll already
 * answers by widening its interval, and its sync treats any 429 as "try later", so neither needs to
 * learn anything new. Everything else is a refusal like any other: an alert under HTMX, the status
 * page on a page load.
 */
private suspend fun ApplicationCall.respondTooManyRequests(waitSeconds: Long) {
    val wait = "Try again in $waitSeconds seconds."
    if (request.path().startsWith("/api/")) {
        respondApiError(HttpStatusCode.TooManyRequests, "slow_down", "Too many requests. $wait")
    } else {
        respondRefusal(
            HttpStatusCode.TooManyRequests,
            title = "Too many requests",
            message = "You are going faster than Seam allows. $wait",
            alertId = "rate-limited-error",
        )
    }
}
