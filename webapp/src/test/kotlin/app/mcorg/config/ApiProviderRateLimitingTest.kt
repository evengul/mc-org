package app.mcorg.config

import app.mcorg.domain.pipeline.Result
import app.mcorg.test.utils.TestUtils
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ApiProviderRateLimitingTest {

    @Serializable
    data class TestResponse(val message: String)

    sealed class TestError {
        data object RateLimit : TestError()
        data object Other : TestError()
    }

    private fun errorMapper(failure: ApiFailure): TestError = when (failure) {
        is ApiFailure.RateLimitExceeded -> TestError.RateLimit
        else -> TestError.Other
    }

    @Test
    fun `rate limiting should block requests when limit exceeded`() {
        val config = TestApiConfig()

        // Use FakeApiProvider to simulate rate limiting scenario
        var requestCount = 0
        val provider = FakeApiProvider(config) { _, _ ->
            requestCount++
            if (requestCount <= 3) {
                Result.success("""{"message": "success"}""")
            } else {
                Result.failure(ApiFailure.RateLimitExceeded)
            }
        }

        val step = provider.get<Unit, TestError, TestResponse>(
            url = "https://api.test.com/endpoint",
            errorMapper = ::errorMapper
        )

        // First 3 requests should succeed
        repeat(3) {
            val value = TestUtils.executeAndAssertSuccess(
                step,
                Unit
            )
            assertEquals("success", value.message)
        }

        // 4th request should fail with rate limit
        val error = TestUtils.executeAndAssertFailure(
            step,
            Unit,
            TestError::class.java
        )
        assertEquals(TestError.RateLimit, error)
    }

    @Test
    fun `rate limiting should handle different API endpoints separately`() {
        val config = TestApiConfig()

        val responseMap = mutableMapOf<String, Int>()
        val provider = FakeApiProvider(config) { _, url ->
            val count = responseMap.getOrDefault(url, 0) + 1
            responseMap[url] = count

            if (count <= 2) {
                Result.success("""{"message": "success from $url"}""")
            } else {
                Result.failure(ApiFailure.RateLimitExceeded)
            }
        }

        val step1 = provider.get<Unit, TestError, TestResponse>(
            url = "https://api.test.com/endpoint1",
            errorMapper = ::errorMapper
        )

        val step2 = provider.get<Unit, TestError, TestResponse>(
            url = "https://api.test.com/endpoint2",
            errorMapper = ::errorMapper
        )

        // Each endpoint should have its own rate limit
        repeat(2) {
            TestUtils.executeAndAssertSuccess(step1, Unit)
            TestUtils.executeAndAssertSuccess(step2, Unit)
        }

        // Both should now hit rate limits
        val rateLimitResult1 = TestUtils.executeAndAssertFailure(
            step1,
            Unit,
            TestError::class.java
        )
        val rateLimitResult2 = TestUtils.executeAndAssertFailure(
            step1,
            Unit,
            TestError::class.java
        )

        assertEquals(TestError.RateLimit, rateLimitResult1)
        assertEquals(TestError.RateLimit, rateLimitResult2)
    }

    @Test
    fun `concurrent requests should be handled properly`() {
        val config = TestApiConfig()

        var requestCount = 0
        val provider = FakeApiProvider(config) { _, _ ->
            requestCount++
            if (requestCount <= 5) {
                Result.success("""{"message": "concurrent success"}""")
            } else {
                Result.failure(ApiFailure.RateLimitExceeded)
            }
        }

        val step = provider.get<Unit, TestError, TestResponse>(
            url = "https://api.test.com/concurrent",
            errorMapper = ::errorMapper
        )

        // Test concurrent access (though FakeApiProvider doesn't actually test real concurrency)
        val results = runBlocking {
            (1..10).map { step.process(Unit) }
        }

        val successes = results.count { it is Result.Success }
        val failures = results.count { it is Result.Failure }

        assertEquals(5, successes)
        assertEquals(5, failures)
    }

    @Test
    fun `HTTP methods should be preserved in requests`() {
        val config = TestApiConfig()

        var capturedMethod: HttpMethod? = null
        val provider = FakeApiProvider(config) { method, _ ->
            capturedMethod = method
            Result.success("""{"message": "method captured"}""")
        }

        // Test GET request
        val getStep = provider.get<Unit, TestError, TestResponse>(
            url = "https://api.test.com/get",
            errorMapper = ::errorMapper
        )
        runBlocking { getStep.process(Unit) }
        assertEquals(HttpMethod.Get, capturedMethod)

        // Test POST request
        val postStep = provider.post<Unit, TestError, TestResponse>(
            url = "https://api.test.com/post",
            errorMapper = ::errorMapper
        )
        runBlocking { postStep.process(Unit) }
        assertEquals(HttpMethod.Post, capturedMethod)
    }

    @Test
    fun `header and body builders should be called for POST requests`() {
        val config = TestApiConfig()

        var headerBuilderCalled = false
        var bodyBuilderCalled = false

        val provider = FakeApiProvider(config) { _, _ ->
            Result.success("""{"message": "builders called"}""")
        }

        val step = provider.post<String, TestError, TestResponse>(
            url = "https://api.test.com/post",
            headerBuilder = { _, _ -> headerBuilderCalled = true },
            bodyBuilder = { _, _ -> bodyBuilderCalled = true },
            errorMapper = ::errorMapper
        )

        val result = TestUtils.executeAndAssertSuccess(
            step,
            "test input"
        )

        assertEquals("builders called", result.message)
        assertEquals(true, headerBuilderCalled)
        assertEquals(true, bodyBuilderCalled)
    }

    @Test
    fun `different failure types should be mapped correctly`() {
        val config = TestApiConfig()

        val failureTypes = listOf(
            ApiFailure.NetworkError,
            ApiFailure.TimeoutError,
            ApiFailure.RateLimitExceeded,
            ApiFailure.HttpError(404, "Not Found"),
            ApiFailure.SerializationError,
            ApiFailure.UnknownError
        )

        failureTypes.forEach { failureType ->
            val provider = FakeApiProvider(config) { _, _ ->
                Result.failure(failureType)
            }

            val step = provider.get<Unit, TestError, TestResponse>(
                url = "https://api.test.com/test",
                errorMapper = ::errorMapper
            )

            val result = TestUtils.executeAndAssertFailure(
                step,
                Unit,
                TestError::class.java
            )

            val expectedError = if (failureType is ApiFailure.RateLimitExceeded) {
                TestError.RateLimit
            } else {
                TestError.Other
            }
            assertEquals(expectedError, result)
        }
    }
}
