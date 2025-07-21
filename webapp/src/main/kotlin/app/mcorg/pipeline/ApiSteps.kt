package app.mcorg.pipeline

import app.mcorg.config.ApiProvider
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.ApiFailure
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object ApiSteps {
    private val logger = LoggerFactory.getLogger("ApiSteps")

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

    // Rate limiting state
    private val rateLimitState = ConcurrentHashMap<String, RateLimitInfo>()
    private val rateLimitMutex = Mutex()

    // Public JSON instance for inline functions to access
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private data class RateLimitInfo(
        val limit: Int,
        val remaining: Int,
        val resetTime: Instant
    )

    fun <I, E> get(
        apiProvider: ApiProvider,
        url: String,
        headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        errorMapper: (ApiFailure) -> E
    ): Step<I, E, String> {
        return request(HttpMethod.Get, apiProvider, url, headerBuilder, { _, _ -> }, errorMapper)
    }

    fun <I, E> post(
        apiProvider: ApiProvider,
        url: String,
        headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        bodyBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        errorMapper: (ApiFailure) -> E
    ): Step<I, E, String> {
        return request(HttpMethod.Post, apiProvider, url, headerBuilder, bodyBuilder, errorMapper)
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

    inline fun <I, E, reified T> getJson(
        apiProvider: ApiProvider,
        url: String,
        noinline headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        noinline errorMapper: (ApiFailure) -> E
    ): Step<I, E, T> {
        return object : Step<I, E, T> {
            override suspend fun process(input: I): Result<E, T> {
                return get(apiProvider, url, headerBuilder, errorMapper)
                    .process(input)
                    .flatMap { responseBody ->
                        deserializeJson<E, T>(responseBody, errorMapper)
                    }
            }
        }
    }

    inline fun <I, E, reified T> postJson(
        apiProvider: ApiProvider,
        url: String,
        noinline headerBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        noinline bodyBuilder: (HttpRequestBuilder, I) -> Unit = { _, _ -> },
        noinline errorMapper: (ApiFailure) -> E
    ): Step<I, E, T> {
        return object : Step<I, E, T> {
            override suspend fun process(input: I): Result<E, T> {
                return post(apiProvider, url, headerBuilder, bodyBuilder, errorMapper)
                    .process(input)
                    .flatMap { responseBody ->
                        deserializeJson<E, T>(responseBody, errorMapper)
                    }
            }
        }
    }

    private fun <I, E> request(
        method: HttpMethod,
        apiProvider: ApiProvider,
        url: String,
        headerBuilder: (HttpRequestBuilder, I) -> Unit,
        bodyBuilder: (HttpRequestBuilder, I) -> Unit,
        errorMapper: (ApiFailure) -> E
    ): Step<I, E, String> {
        return object : Step<I, E, String> {
            override suspend fun process(input: I): Result<E, String> {
                return try {
                    // Check rate limiting before making the request
                    val rateLimitCheck = checkRateLimit(apiProvider.getBaseUrl())
                    if (!rateLimitCheck) {
                        return Result.failure(errorMapper(ApiFailure.RateLimitExceeded))
                    }

                    val response = httpClient.request(url) {
                        this.method = method
                        contentType(apiProvider.getContentType())
                        accept(apiProvider.acceptContentType())

                        // Set User-Agent if provided
                        apiProvider.getUserAgent()?.let { userAgent ->
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
                    updateRateLimit(apiProvider.getBaseUrl(), response)

                    if (response.status.isSuccess()) {
                        Result.success(response.bodyAsText())
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

    fun cleanup() {
        httpClient.close()
    }
}
