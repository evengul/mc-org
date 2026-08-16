package app.mcorg.config

import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ConnectException
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

    inline fun <I, reified S> post(
        url: String,
        noinline headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        noinline bodyBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
    ) : Step<I, AppFailure.ApiError, S> {
        return object : Step<I, AppFailure.ApiError, S> {
            override suspend fun process(input: I): Result<AppFailure.ApiError, S> {
                val result = request(
                    HttpMethod.Post,
                    url,
                    headerBuilder,
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
