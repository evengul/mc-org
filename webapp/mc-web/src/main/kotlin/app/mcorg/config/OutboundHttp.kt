package app.mcorg.config

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.statement.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Every byte the webapp sends to another host leaves through here (MCO-552).
 *
 * Three outbound paths, two clients, one engine, one owner:
 *
 *  * [api] — the JSON services behind [ApiProvider]: Microsoft, Xbox, XSTS, Minecraft services and
 *    Mojang's piston-meta. 30s request / 10s connect / 30s socket, bounded [HttpRequestRetry]
 *    (MCO-354) and content negotiation. The Mojang server.jar download (`GetServerFileStep`)
 *    streams through this client too, overriding the timeouts per request: the engine and the
 *    retry are what it wants from the shared client, the JSON-shaped `Step` surface of
 *    [ApiProvider] is not, so it calls the client directly. It used to be a `java.net.URL`
 *    connection with its own timeouts and no retry.
 *  * [webhook] — signed POSTs to subscriber callback URLs (`WebhookDeliveryPoller`). Deliberately
 *    a *separate client* on the same engine, because it wants the opposite policy on every axis:
 *    5s for every timeout (a receiver is someone else's server, and the outbox loop must not park
 *    30s on it); **no transport retry**, because the outbox *is* the retry — `webhook_deliveries`
 *    reschedules a failed row immediate → 30s → 5min and gives up after three attempts
 *    (documentation/webhook-contract.md), and a replay underneath that would double-deliver and
 *    spend the receiver's dedup key on our own duplicates; no rate limiter, because the limiter is
 *    keyed by an `ApiConfig.baseUrl` and callback URLs are arbitrary user-supplied hosts; and no
 *    content negotiation, because the body is a pre-signed string that must go out byte-for-byte.
 *    Ktor can express all four per request, but four per-request overrides on every webhook POST
 *    is a policy hidden in a call site; a second client makes the policy the object.
 *  * [engine] — the one CIO engine both clients run on: one selector, one connection pool, one
 *    thing to close. Neither client owns it (`HttpClient(engine)` leaves engine lifetime to the
 *    caller), which is what makes [shutdown] the single place that ends outbound I/O.
 *
 * Lifecycle: the clients are process-wide singletons, like [Database]. The web app closes them on
 * `ApplicationStopped` via [configureOutboundHttp] — after `ApplicationStopping` has cancelled the
 * webhook and event scopes, so an in-flight delivery is cancelled by its scope (and rethrown, not
 * counted as a failure) rather than by a closed client. The ingest CLI has no Ktor lifecycle and
 * calls [shutdown] from its `shutdown` lambda next to `Database.shutdown()`. `testApplication`
 * never runs `module()`, so tests never close them; the JVM ending does.
 *
 * `User-Agent`: every path sends [USER_AGENT]. Before this, the two Ktor clients sent Ktor's
 * default `Ktor client` and the JDK download sent `Java/<version>`; nothing named Seam.
 */
object OutboundHttp {
    /** Identifies Seam to Microsoft, Mojang and webhook receivers. Static — there is no build version to carry. */
    const val USER_AGENT = "Seam (+https://app.seam.gg)"

    /** Covers a dead endpoint. Shared by the API client and the server.jar download. */
    const val CONNECT_TIMEOUT_MS = 10_000L

    /** The API client's request and socket ceiling. A sign-in hop that takes longer than this has failed. */
    const val API_TIMEOUT_MS = 30_000L

    /** Every webhook timeout. Mirrored in documentation/webhook-contract.md — change both or neither. */
    const val WEBHOOK_TIMEOUT_MS = 5_000L

    val engine: HttpClientEngine = CIO.create()

    /** The JSON API client. See the class comment for what runs through it. */
    val api: HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = API_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = API_TIMEOUT_MS
        }
        // MCO-354. Nothing outbound retried before this, so a single 502 from Xbox Live —
        // historically flaky — ended a sign-in outright.
        install(HttpRequestRetry) {
            maxRetries = MAX_RETRIES
            retryIf { _, response -> response.request.isReplayable() && isTransientStatus(response.status) }
            retryOnExceptionIf { request, cause -> request.build().isReplayable() && isTransientCause(cause) }
            exponentialDelay(maxDelayMs = RETRY_MAX_DELAY_MS, randomizationMs = RETRY_JITTER_MS)
        }
    }

    /** The webhook delivery client. See the class comment for why it is not [api]. */
    val webhook: HttpClient = HttpClient(engine) {
        install(HttpTimeout) {
            requestTimeoutMillis = WEBHOOK_TIMEOUT_MS
            connectTimeoutMillis = WEBHOOK_TIMEOUT_MS
            socketTimeoutMillis = WEBHOOK_TIMEOUT_MS
        }
    }

    /** Idempotent: Ktor's `close()` is a compare-and-set on both the client and the engine. */
    fun shutdown() {
        // Logged so a production shutdown shows the hook ran; nothing else in this path speaks.
        logger.info("Closing outbound HTTP clients")
        api.close()
        webhook.close()
        engine.close()
    }

    private val logger = LoggerFactory.getLogger(OutboundHttp::class.java)
}

/** Ties [OutboundHttp.shutdown] to the Ktor lifecycle. Call once from `Application.module()`. */
fun Application.configureOutboundHttp() {
    monitor.subscribe(ApplicationStopped) { OutboundHttp.shutdown() }
}

/*
 * Bounded retry with jittered backoff (MCO-354).
 *
 * Two attempts after the first, so three in total. The sign-in flow is five sequential upstream
 * calls across four providers and any one of them returning 502/503 ends it at
 * "?error=external_api_error", so the win is large — but it is five calls deep, and multiplying
 * each one's worst case is how a slow sign-in becomes an abandoned one.
 *
 * What the sign-in button actually waits for (MCO-552 audit). Only *fast* failures are retried —
 * a 5xx/429 response or a refused/reset connection — never a timeout, so a retry costs the backoff
 * and little else. Ktor's exponentialDelay is base 2 from 1s, capped at RETRY_MAX_DELAY_MS, so
 * both retries wait 2s (+ ≤250ms jitter): a hop that is down adds ~4.5s, and the four retryable
 * hops (the Microsoft token exchange is not one) add ~18s in the worst case before the flow fails
 * as it would have anyway. The one open edge is `respectRetryAfterHeader`, on by default: a 429
 * carrying `Retry-After: 60` holds the button for 60s rather than 2s. Left as is — honouring the
 * header is what keeps a rate-limited account from being limited harder, and none of the four
 * providers has been seen sending one.
 */
private const val MAX_RETRIES = 2
private const val RETRY_MAX_DELAY_MS = 2_000L

/**
 * Jitter width. Without it, a transient upstream blip makes every in-flight request retry in
 * lockstep and arrive together — the retry becomes the second outage.
 */
private const val RETRY_JITTER_MS = 250L

/**
 * Marks a non-idempotent request as safe to replay.
 *
 * GETs are retried automatically. POSTs are not, because the default has to be the safe one: the
 * Microsoft token exchange spends a single-use authorization `code`, and replaying it turns a
 * recoverable upstream blip into a failed sign-in. Opting in is per call site, so adding a new
 * POST cannot accidentally inherit retry.
 */
val RETRY_SAFE = AttributeKey<Boolean>("SeamRetrySafeRequest")

/** GET/HEAD/OPTIONS are idempotent by definition; anything else must opt in via [RETRY_SAFE]. */
private fun HttpRequest.isReplayable(): Boolean =
    method.isIdempotent() || attributes.getOrNull(RETRY_SAFE) == true

private fun HttpRequestData.isReplayable(): Boolean =
    method.isIdempotent() || attributes.getOrNull(RETRY_SAFE) == true

private fun HttpMethod.isIdempotent(): Boolean =
    this == HttpMethod.Get || this == HttpMethod.Head || this == HttpMethod.Options

/** 5xx and 429 are "try again"; every 4xx is "this request is wrong" and will stay wrong. */
private fun isTransientStatus(status: HttpStatusCode): Boolean =
    status.value >= 500 || status == HttpStatusCode.TooManyRequests

/**
 * Retry a connection that failed, never one that timed out.
 *
 * A refused or reset connection fails in milliseconds, so replaying it is nearly free and fits
 * inside the existing 30s request timeout several times over. A *timeout* has already spent that
 * budget — retrying it would turn one 30s wait into ninety, on a sign-in path a user is actively
 * waiting on. `HttpRequestTimeoutException` extends `IOException`, so it has to be excluded
 * explicitly rather than by leaving it out.
 */
private fun isTransientCause(cause: Throwable): Boolean = when (cause) {
    is HttpRequestTimeoutException -> false
    is ConnectTimeoutException -> false
    is SocketTimeoutException -> false
    is IOException -> true
    else -> false
}
