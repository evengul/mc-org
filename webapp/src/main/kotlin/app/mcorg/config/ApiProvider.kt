package app.mcorg.config

import app.mcorg.domain.pipeline.Result
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
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private data class RateLimitInfo(
    val limit: Int,
    val remaining: Int,
    val resetTime: Instant
)

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
                    is Result.Success -> deserializeJson(result.value)
                    is Result.Failure -> Result.failure(result.error)
                }
            }
        }
    }

    fun <I> getRaw(
        url: String,
        headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
    ) : Step<I, AppFailure.ApiError, InputStream> {
        return request(
            HttpMethod.Get,
            url,
            headerBuilder,
            { _, _ -> },
            resultMapper = { response -> response.readRawBytes().inputStream() }
        )
    }

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
                    is Result.Success -> deserializeJson(result.value)
                    is Result.Failure -> Result.failure(result.error)
                }
            }
        }
    }

    inline fun <reified T> deserializeJson(
        responseBody: String,
    ): Result<AppFailure.ApiError.SerializationError, T> {
        return try {
            val result = json.decodeFromString<T>(responseBody)
            Result.success(result)
        } catch (e: SerializationException) {
            println("Failed to deserialize JSON response: ${e.message}")
            Result.failure(AppFailure.ApiError.SerializationError)
        } catch (e: Exception) {
            println("Failed to deserialize JSON response: ${e.message}")
            Result.failure(AppFailure.ApiError.SerializationError)
        }
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
    // Rate limiting state
    private val rateLimitState = ConcurrentHashMap<String, RateLimitInfo>()
    private val rateLimitMutex = Mutex()

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 30000
        }
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
                        logger.warn("API request failed with status ${response.status.value}: $errorBody")
                        Result.failure(AppFailure.ApiError.HttpError(response.status.value, errorBody))
                    }
                } catch (e: HttpRequestTimeoutException) {
                    logger.error("API request timed out for URL: $url", e)
                    Result.failure(AppFailure.ApiError.TimeoutError)
                } catch (e: Exception) {
                    logger.error("API request failed for URL: $url", e)
                    Result.failure(when (e) {
                        is java.net.ConnectException,
                        is java.net.UnknownHostException -> AppFailure.ApiError.NetworkError
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