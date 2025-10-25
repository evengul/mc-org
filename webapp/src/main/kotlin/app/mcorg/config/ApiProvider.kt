package app.mcorg.config

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.ApiFailure
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.text.toIntOrNull
import kotlin.text.toLongOrNull

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

    inline fun <I, E, reified S> get(
        url: String,
        noinline headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        noinline errorMapper: (ApiFailure) -> E
    ) : Step<I, E, S> {
        return object : Step<I, E, S> {
            override suspend fun process(input: I): Result<E, S> {
                val result = request(
                    HttpMethod.Get,
                    url,
                    headerBuilder,
                    { _, _ -> },
                    errorMapper
                ).process(input)

                return when(result) {
                    is Result.Success -> deserializeJson(result.value, errorMapper)
                    is Result.Failure -> Result.failure(result.error)
                }
            }
        }
    }

    fun <I, E> getRaw(
        url: String,
        headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        errorMapper: (ApiFailure) -> E
    ) : Step<I, E, InputStream> {
        return request(
            HttpMethod.Get,
            url,
            headerBuilder,
            { _, _ -> },
            errorMapper,
            resultMapper = { response -> response.readRawBytes().inputStream() }
        )
    }

    inline fun <I, E, reified S> post(
        url: String,
        noinline headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        noinline bodyBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        noinline errorMapper: (ApiFailure) -> E
    ) : Step<I, E, S> {
        return object : Step<I, E, S> {
            override suspend fun process(input: I): Result<E, S> {
                val result = request(
                    HttpMethod.Post,
                    url,
                    headerBuilder,
                    bodyBuilder,
                    errorMapper
                ).process(input)

                return when(result) {
                    is Result.Success -> deserializeJson(result.value, errorMapper)
                    is Result.Failure -> Result.failure(result.error)
                }
            }
        }
    }

    inline fun <E, reified T> deserializeJson(
        responseBody: String,
        noinline errorMapper: (ApiFailure) -> E
    ): Result<E, T> {
        return try {
            val result = json.decodeFromString<T>(responseBody)
            Result.success(result)
        } catch (e: SerializationException) {
            println("Failed to deserialize JSON response: ${e.message}")
            Result.failure(errorMapper(ApiFailure.SerializationError))
        } catch (e: Exception) {
            println("Failed to deserialize JSON response: ${e.message}")
            Result.failure(errorMapper(ApiFailure.SerializationError))
        }
    }

    fun <I, E> request(
        method: HttpMethod,
        url: String,
        headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        bodyBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        errorMapper: (ApiFailure) -> E
    ) : Step<I, E, String> {
        return request(
            method,
            url,
            headerBuilder,
            bodyBuilder,
            errorMapper
        ) { response -> response.bodyAsText() }
    }

    abstract fun <I, E, S> request(
        method: HttpMethod,
        url: String,
        headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        bodyBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        errorMapper: (ApiFailure) -> E,
        resultMapper: suspend (HttpResponse) -> S
    ) : Step<I, E, S>
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

    override fun <I, E, S> request(
        method: HttpMethod,
        url: String,
        headerBuilder: (HttpRequestBuilder, I) -> Unit,
        bodyBuilder: (HttpRequestBuilder, I) -> Unit,
        errorMapper: (ApiFailure) -> E,
        resultMapper: suspend (HttpResponse) -> S
    ): Step<I, E, S> {
        return object : Step<I, E, S> {
            override suspend fun process(input: I): Result<E, S> {
                return try {
                    // Check rate limiting before making the request
                    val rateLimitCheck = checkRateLimit(config.baseUrl)
                    if (!rateLimitCheck) {
                        return Result.failure(errorMapper(ApiFailure.RateLimitExceeded))
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
                        Result.failure(errorMapper(ApiFailure.HttpError(response.status.value, errorBody)))
                    }
                } catch (e: HttpRequestTimeoutException) {
                    logger.error("API request timed out for URL: $url", e)
                    Result.failure(errorMapper(ApiFailure.TimeoutError))
                } catch (e: Exception) {
                    logger.error("API request failed for URL: $url", e)
                    val failure = when (e) {
                        is java.net.ConnectException,
                        is java.net.UnknownHostException -> ApiFailure.NetworkError
                        else -> ApiFailure.UnknownError
                    }
                    Result.failure(errorMapper(failure))
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

class FakeApiProvider(
    config: ApiConfig,
    private val getResponseBody: (method: HttpMethod, url: String) -> Result<ApiFailure, String>
) : ApiProvider(config) {
    override fun <I, E, S> request(
        method: HttpMethod,
        url: String,
        headerBuilder: (HttpRequestBuilder, I) -> Unit,
        bodyBuilder: (HttpRequestBuilder, I) -> Unit,
        errorMapper: (ApiFailure) -> E,
        resultMapper: suspend (HttpResponse) -> S
    ): Step<I, E, S> {
        return object : Step<I, E, S> {
            override suspend fun process(input: I): Result<E, S> {
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
                return getResponseBody(method, url).mapError { errorMapper(it) } as Result<E, S>
            }
        }
    }
}