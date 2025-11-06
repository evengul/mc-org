package app.mcorg.config

import app.mcorg.domain.pipeline.Result
import app.mcorg.test.utils.TestUtils
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApiProviderTest {

    // Test data classes
    @Serializable
    data class TestResponse(val id: Int, val name: String)
    
    @Serializable
    data class TestRequest(val data: String)
    
    sealed class TestError {
        data object NetworkFailure : TestError()
        data object ValidationFailure : TestError()
        data object SerializationFailure : TestError()
        data object RateLimit : TestError()
        data object Timeout : TestError()
        data object Unknown : TestError()
    }

    private fun errorMapper(failure: ApiFailure): TestError = when (failure) {
        is ApiFailure.NetworkError -> TestError.NetworkFailure
        is ApiFailure.TimeoutError -> TestError.Timeout
        is ApiFailure.RateLimitExceeded -> TestError.RateLimit
        is ApiFailure.HttpError -> TestError.ValidationFailure
        is ApiFailure.SerializationError -> TestError.SerializationFailure
        is ApiFailure.UnknownError -> TestError.Unknown
    }

    @Test
    fun `deserializeJson should successfully deserialize valid JSON`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ -> Result.success("") }
        
        val jsonString = """{"id": 123, "name": "Test Item"}"""
        
        val result = provider.deserializeJson<TestResponse>(jsonString, ::errorMapper)
        
        assertIs<Result.Success<TestResponse>>(result)
        assertEquals(123, result.value.id)
        assertEquals("Test Item", result.value.name)
    }

    @Test
    fun `deserializeJson should handle serialization errors`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ -> Result.success("") }
        
        val invalidJson = """{"invalid": "json", "missing": "fields"}"""
        
        val result = provider.deserializeJson<TestResponse>(invalidJson, ::errorMapper)
        
        assertIs<Result.Failure<TestError>>(result)
        assertEquals(TestError.SerializationFailure, result.error)
    }

    @Test
    fun `deserializeJson should handle malformed JSON`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ -> Result.success("") }
        
        val malformedJson = """{"incomplete": json"""
        
        val result = provider.deserializeJson<TestResponse>(malformedJson, ::errorMapper)
        
        assertIs<Result.Failure<TestError>>(result)
        assertEquals(TestError.SerializationFailure, result.error)
    }

    @Test
    fun `get should return success for valid response`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { method, url ->
            assertEquals(HttpMethod.Get, method)
            assertEquals("https://api.test.com/items/123", url)
            Result.success("""{"id": 123, "name": "Test Item"}""")
        }
        
        val step = provider.get<Unit, TestError, TestResponse>(
            url = "https://api.test.com/items/123",
            errorMapper = ::errorMapper
        )
        
        val result = TestUtils.executeAndAssertSuccess(step, Unit)
        
        assertEquals(123, result.id)
        assertEquals("Test Item", result.name)
    }

    @Test
    fun `get should return failure when API returns error`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ ->
            Result.failure(ApiFailure.NetworkError)
        }
        
        val step = provider.get<Unit, TestError, TestResponse>(
            url = "https://api.test.com/items/123",
            errorMapper = ::errorMapper
        )
        
        val result = TestUtils.executeAndAssertFailure(step, Unit, TestError::class.java)
        
        assertEquals(TestError.NetworkFailure, result)
    }

    @Test
    fun `post should return success for valid response`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { method, url ->
            assertEquals(HttpMethod.Post, method)
            assertEquals("https://api.test.com/items", url)
            Result.success("""{"id": 456, "name": "Created Item"}""")
        }
        
        val step = provider.post<TestRequest, TestError, TestResponse>(
            url = "https://api.test.com/items",
            errorMapper = ::errorMapper
        )
        
        val result = TestUtils.executeAndAssertSuccess(step, TestRequest("test data"))
        
        assertEquals(456, result.id)
        assertEquals("Created Item", result.name)
    }

    @Test
    fun `post should handle deserialization errors`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ ->
            Result.success("""{"invalid": "response"}""")
        }
        
        val step = provider.post<TestRequest, TestError, TestResponse>(
            url = "https://api.test.com/items",
            errorMapper = ::errorMapper
        )

        TestUtils.executeAndAssertFailure(step, TestRequest("test data"), TestError.SerializationFailure::class.java)
    }

    @Test
    fun `FakeApiProvider should call error mapper correctly`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ ->
            Result.failure(ApiFailure.TimeoutError)
        }
        
        val step = provider.request<Unit>(
            method = HttpMethod.Get,
            url = "https://api.test.com/test",
            errorMapper = ::errorMapper
        )

        TestUtils.executeAndAssertFailure(step, Unit, TestError.Timeout::class.java)
    }

    @Test
    fun `FakeApiProvider should pass through success responses`() {
        val config = TestApiConfig()
        val testResponse = "success response body"
        val provider = FakeApiProvider(config) { _, _ ->
            Result.success(testResponse)
        }
        
        val step = provider.request<Unit>(
            method = HttpMethod.Get,
            url = "https://api.test.com/test",
            errorMapper = ::errorMapper
        )
        
        val result = TestUtils.executeAndAssertSuccess(step, Unit)
        
        assertEquals(testResponse, result)
    }

    @Test
    fun `FakeApiProvider should receive correct method and URL`() {
        val config = TestApiConfig()
        var receivedMethod: HttpMethod? = null
        var receivedUrl: String? = null
        
        val provider = FakeApiProvider(config) { method, url ->
            receivedMethod = method
            receivedUrl = url
            Result.success("test")
        }
        
        val step = provider.request<Unit>(
            method = HttpMethod.Post,
            url = "https://example.com/api/endpoint",
            errorMapper = ::errorMapper
        )
        
        runBlocking {
            step.process(Unit)
        }
        
        assertEquals(HttpMethod.Post, receivedMethod)
        assertEquals("https://example.com/api/endpoint", receivedUrl)
    }
}
