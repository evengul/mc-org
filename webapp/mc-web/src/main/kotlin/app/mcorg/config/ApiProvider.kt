package app.mcorg.config

import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.AttributeKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private data class RateLimitInfo(
    val limit: Int,
    val remaining: Int,
    val resetTime: Instant
)

/** Upper bound on how much of an upstream error body reaches the DEBUG log (MCO-338). */
const val ERROR_BODY_LOG_LIMIT = 256

/*
 * Bounded retry with jittered backoff (MCO-354).
 *
 * Two attempts after the first, so three in total. The sign-in flow is five sequential upstream
 * calls across four providers and any one of them returning 502/503 ends it at
 * "?error=external_api_error", so the win is large — but it is five calls deep, and multiplying
 * each one's worst case is how a slow sign-in becomes an abandoned one.
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
    method == HttpMethod.Get ||
        method == HttpMethod.Head ||
        method == HttpMethod.Options ||
        attributes.getOrNull(RETRY_SAFE) == true

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

sealed class ApiProvider(
    protected val config: ApiConfig
) {
    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    inline fun <I, reified S> get(
        url: String,
        noinline headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
    ) : Step<I, AppFailure.ApiError, S> {
        return object : Step<I, AppFailure.ApiError, S> {
            override suspend fun process(input: I): Result<AppFailure.ApiError, S> {
                val result = request(
                    HttpMethod.Get,
                    url,
                    headerBuilder,
                    { _, _ -> },
                ).process(input)

                return when(result) {
                    is Result.Success -> deserializeJson(result.value, url)
                    is Result.Failure -> Result.failure(result.error)
                }
            }
        }
    }

    /*
     * There is deliberately no raw-bytes fetch here (MCO-346).
     *
     * `getRaw()` used to be, with zero call sites and a `readRawBytes().inputStream()` body that
     * buffered the whole response into the heap. The one caller it could ever have had is the
     * Mojang server.jar download in GetServerFilesPipeline, and that call wants the opposite of
     * everything this class provides: a multi-minute budget rather than the shared 30s
     * HttpTimeout, a stream to a temp file rather than a value in memory, and a SHA-1 digest
     * computed during the transfer. Fitting it to the `Step<I, E, S>` shape would have meant
     * bypassing most of the class anyway.
     *
     * The cost of that boundary is real and worth stating: the Mojang download is outside the
     * shared client, so it does not inherit HttpRequestRetry (MCO-354). It is a nightly,
     * idempotent job that self-heals on the next run, which is why that is an acceptable trade —
     * but a new outbound call should go through this class, not copy the download step.
     */

    /**
     * @param retrySafe opt this POST into the shared client's retry (MCO-354). Set it only when
     * replaying the exact same body is harmless — a token *exchange* that consumes a single-use
     * code is not, an auth handshake that re-presents a token it already holds is. Defaults to
     * false so a new POST is never retried by accident.
     */
    inline fun <I, reified S> post(
        url: String,
        noinline headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        noinline bodyBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        retrySafe: Boolean = false,
    ) : Step<I, AppFailure.ApiError, S> {
        val withRetryMarker: (HttpRequestBuilder, I) -> Unit = { builder, input ->
            if (retrySafe) builder.attributes.put(RETRY_SAFE, true)
            headerBuilder(builder, input)
        }
        return object : Step<I, AppFailure.ApiError, S> {
            override suspend fun process(input: I): Result<AppFailure.ApiError, S> {
                val result = request(
                    HttpMethod.Post,
                    url,
                    withRetryMarker,
                    bodyBuilder,
                ).process(input)

                return when(result) {
                    is Result.Success -> deserializeJson(result.value, url)
                    is Result.Failure -> Result.failure(result.error)
                }
            }
        }
    }

    inline fun <reified T> deserializeJson(
        responseBody: String,
        url: String? = null,
    ): Result<AppFailure.ApiError.SerializationError, T> {
        return try {
            val result = json.decodeFromString<T>(responseBody)
            Result.success(result)
        } catch (_: Exception) {
            // Deliberately no exception, no message, no body — see logDeserializationFailure.
            // (SerializationException is an Exception, so one catch covers both cases; they
            // previously did the same thing anyway.)
            logDeserializationFailure(T::class.simpleName ?: "an unnamed type", url)
            Result.failure(AppFailure.ApiError.SerializationError)
        }
    }

    /**
     * Logs a deserialization failure **without** the exception, its message, or the response body.
     *
     * This is not excessive caution (MCO-336). `MinecraftSignInPipeline` routes all four OAuth
     * token exchanges through [deserializeJson] — the Microsoft, Xbox, XSTS and Minecraft
     * responses, carrying `access_token`, `id_token` and `Token`. kotlinx-serialization's
     * `formatDecodingException` appends `"\nJSON input: " + input` to the message, guarded by
     * `JsonConfiguration.exceptionsWithDebugInfo`, which **defaults to true** and which this
     * class's `Json { ignoreUnknownKeys; isLenient }` does not disable. So `e.message` on a
     * decoding failure contains the token payload — and so does the stack trace, since that
     * renders the same `getMessage()`. Passing `e` as a second argument to the logger leaks it
     * just as thoroughly as interpolating it.
     *
     * The trigger is realistic rather than theoretical: `isLenient = true` means a non-JSON body
     * — a CDN or proxy error page in front of `login.microsoftonline.com` — fails here *with the
     * input attached*.
     *
     * `@PublishedApi internal` because a public inline function cannot touch the protected
     * [logger].
     */
    @PublishedApi
    internal fun logDeserializationFailure(targetType: String, url: String?) {
        logger.warn(
            "Failed to deserialize a response into {}{}. Body and exception omitted deliberately: " +
                "they can carry OAuth tokens (MCO-336).",
            targetType,
            url?.let { " from $it" } ?: "",
        )
    }

    fun <I> request(
        method: HttpMethod,
        url: String,
        headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        bodyBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
    ) : Step<I, AppFailure.ApiError, String> {
        return request(
            method,
            url,
            headerBuilder,
            bodyBuilder,
        ) { response -> response.bodyAsText() }
    }

    abstract fun <I, S> request(
        method: HttpMethod,
        url: String,
        headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        bodyBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        resultMapper: suspend (HttpResponse) -> S
    ) : Step<I, AppFailure.ApiError, S>
}

class DefaultApiProvider(
    config: ApiConfig
) : ApiProvider(config) {
    companion object {
        // A single HttpClient shared across all providers: one connection pool + TLS-session
        // cache reused app-wide, instead of constructing (and leaking) a new client per call.
        private val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 30000
            }
            // MCO-354. Nothing outbound retried before this, so a single 502 from Xbox Live —
            // historically flaky — ended a sign-in outright.
            install(HttpRequestRetry) {
                maxRetries = MAX_RETRIES
                retryIf { _, response -> response.request.isReplayable() && isTransientStatus(response.status) }
                retryOnExceptionIf { request, cause ->
                    request.build().let { built ->
                        (built.method == HttpMethod.Get ||
                            built.method == HttpMethod.Head ||
                            built.method == HttpMethod.Options ||
                            built.attributes.getOrNull(RETRY_SAFE) == true) && isTransientCause(cause)
                    }
                }
                exponentialDelay(maxDelayMs = RETRY_MAX_DELAY_MS, randomizationMs = RETRY_JITTER_MS)
            }
        }

        // Rate limiting state, keyed by baseUrl and shared so the window persists across
        // calls (each getProvider() previously returned a fresh instance with empty state).
        private val rateLimitState = ConcurrentHashMap<String, RateLimitInfo>()
        private val rateLimitMutex = Mutex()
    }

    override fun <I, S> request(
        method: HttpMethod,
        url: String,
        headerBuilder: (HttpRequestBuilder, I) -> Unit,
        bodyBuilder: (HttpRequestBuilder, I) -> Unit,
        resultMapper: suspend (HttpResponse) -> S
    ): Step<I, AppFailure.ApiError, S> {
        return object : Step<I, AppFailure.ApiError, S> {
            override suspend fun process(input: I): Result<AppFailure.ApiError, S> {
                return try {
                    // Check rate limiting before making the request
                    val rateLimitCheck = checkRateLimit(config.baseUrl)
                    if (!rateLimitCheck) {
                        return Result.failure(AppFailure.ApiError.RateLimitExceeded)
                    }

                    val response = httpClient.request(url) {
                        this.method = method
                        contentType(config.getContentType())
                        accept(config.acceptContentType())

                        // Set User-Agent if provided
                        config.getUserAgent()?.let { userAgent ->
                            header(HttpHeaders.UserAgent, userAgent)
                        }

                        // Apply custom headers
                        headerBuilder(this, input)

                        // Apply body for POST requests
                        if (method == HttpMethod.Post) {
                            bodyBuilder(this, input)
                        }
                    }

                    // Update rate limiting information from response headers
                    updateRateLimit(config.baseUrl, response)

                    if (response.status.isSuccess()) {
                        Result.success(resultMapper(response))
                    } else {
                        val errorBody = try {
                            response.bodyAsText()
                        } catch (e: Exception) {
                            logger.error("Failed to read error body for URL: $url", e)
                            null
                        }
                        // MCO-338. Two changes to what used to be one unbounded WARN line:
                        //
                        //  * The URL is logged. It was missing, so a production log could not
                        //    distinguish Xbox from XSTS from Minecraft services — every one of
                        //    them just said "API request failed with status 401".
                        //  * The body is no longer in the WARN. It is unbounded and comes from
                        //    auth endpoints: Microsoft 400s carry AADSTS text with trace and
                        //    correlation ids, Xbox/XSTS 401s carry XErr codes tied to the
                        //    account. At WARN no level policy suppresses it, so it would be
                        //    retained verbatim once logs ship to Axiom.
                        //
                        // The body is still available: truncated at DEBUG (off in production by
                        // B2's level policy, on locally), and in full on the HttpError failure
                        // for callers that need to branch on it.
                        logger.warn(
                            "API request to {} failed with status {} ({} bytes of body, not logged)",
                            url,
                            response.status.value,
                            errorBody?.length ?: 0,
                        )
                        if (errorBody != null && logger.isDebugEnabled) {
                            logger.debug(
                                "Error body from {} (truncated to {} chars): {}",
                                url,
                                ERROR_BODY_LOG_LIMIT,
                                errorBody.take(ERROR_BODY_LOG_LIMIT),
                            )
                        }
                        Result.failure(AppFailure.ApiError.HttpError(response.status.value, errorBody))
                    }
                } catch (e: HttpRequestTimeoutException) {
                    logger.error("API request timed out for URL: $url", e)
                    Result.failure(AppFailure.ApiError.TimeoutError)
                } catch (e: Exception) {
                    logger.error("API request failed for URL: $url", e)
                    Result.failure(when (e) {
                        is ConnectException,
                        is UnknownHostException -> AppFailure.ApiError.NetworkError
                        else -> AppFailure.ApiError.UnknownError
                    })
                }
            }
        }
    }

    private suspend fun checkRateLimit(baseUrl: String): Boolean {
        rateLimitMutex.withLock {
            val info = rateLimitState[baseUrl] ?: return true

            val now = Instant.now()
            if (now.isAfter(info.resetTime)) {
                // Rate limit window has reset
                rateLimitState.remove(baseUrl)
                return true
            }

            return info.remaining > 0
        }
    }

    private suspend fun updateRateLimit(baseUrl: String, response: HttpResponse) {
        val limitHeader = response.headers["X-Ratelimit-Limit"]?.toIntOrNull()
        val remainingHeader = response.headers["X-Ratelimit-Remaining"]?.toIntOrNull()
        val resetHeader = response.headers["X-Ratelimit-Reset"]?.toLongOrNull()

        if (limitHeader != null && remainingHeader != null && resetHeader != null) {
            rateLimitMutex.withLock {
                val resetTime = Instant.now().plusSeconds(resetHeader)
                rateLimitState[baseUrl] = RateLimitInfo(limitHeader, remainingHeader, resetTime)
            }
        }
    }
}

class FakeApiProvider<S>(
    config: ApiConfig,
    private val getResponseBody: (method: HttpMethod, url: String) -> Result<AppFailure.ApiError, S>
) : ApiProvider(config) {
    override fun <I, S> request(
        method: HttpMethod,
        url: String,
        headerBuilder: (HttpRequestBuilder, I) -> Unit,
        bodyBuilder: (HttpRequestBuilder, I) -> Unit,
        resultMapper: suspend (HttpResponse) -> S
    ): Step<I, AppFailure.ApiError, S> {
        return object : Step<I, AppFailure.ApiError, S> {
            override suspend fun process(input: I): Result<AppFailure.ApiError, S> {
                val requestBuilder = HttpRequestBuilder().apply {
                    this.method = method
                    contentType(config.getContentType())
                    accept(config.acceptContentType())
                    config.getUserAgent()?.let { userAgent ->
                        header(HttpHeaders.UserAgent, userAgent)
                    }
                }
                headerBuilder(requestBuilder, input)
                bodyBuilder(requestBuilder, input)
                @Suppress("UNCHECKED_CAST")
                return getResponseBody(method, url) as Result<AppFailure.ApiError, S>
            }
        }
    }
}
