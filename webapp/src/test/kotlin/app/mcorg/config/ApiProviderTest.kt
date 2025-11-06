package app.mcorg.config

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
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

    @Test
    fun `deserializeJson should successfully deserialize valid JSON`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ -> Result.success("") }
        
        val jsonString = """{"id": 123, "name": "Test Item"}"""
        
        val result = provider.deserializeJson<TestResponse>(jsonString)
        
        assertIs<Result.Success<TestResponse>>(result)
        assertEquals(123, result.value.id)
        assertEquals("Test Item", result.value.name)
    }

    @Test
    fun `deserializeJson should handle serialization errors`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ -> Result.success("") }
        
        val invalidJson = """{"invalid": "json", "missing": "fields"}"""
        
        val result = provider.deserializeJson<TestResponse>(invalidJson)
        
        assertIs<Result.Failure<AppFailure.ApiError.SerializationError>>(result)
        assertEquals(AppFailure.ApiError.SerializationError, result.error)
    }

    @Test
    fun `deserializeJson should handle malformed JSON`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ -> Result.success("") }
        
        val malformedJson = """{"incomplete": json"""
        
        val result = provider.deserializeJson<TestResponse>(malformedJson)
        
        assertIs<Result.Failure<AppFailure.ApiError.SerializationError>>(result)
        assertEquals(AppFailure.ApiError.SerializationError, result.error)
    }

    @Test
    fun `get should return success for valid response`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { method, url ->
            assertEquals(HttpMethod.Get, method)
            assertEquals("https://api.test.com/items/123", url)
            Result.success("""{"id": 123, "name": "Test Item"}""")
        }
        
        val step = provider.get<Unit, TestResponse>(
            url = "https://api.test.com/items/123"
        )
        
        val result = TestUtils.executeAndAssertSuccess(step, Unit)
        
        assertEquals(123, result.id)
        assertEquals("Test Item", result.name)
    }

    @Test
    fun `get should return failure when API returns error`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ ->
            Result.failure(AppFailure.ApiError.NetworkError)
        }
        
        val step = provider.get<Unit, TestResponse>(
            url = "https://api.test.com/items/123"
        )
        
        val result = TestUtils.executeAndAssertFailure(step, Unit)
        
        assertEquals(AppFailure.ApiError.NetworkError, result)
    }

    @Test
    fun `post should return success for valid response`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { method, url ->
            assertEquals(HttpMethod.Post, method)
            assertEquals("https://api.test.com/items", url)
            Result.success("""{"id": 456, "name": "Created Item"}""")
        }
        
        val step = provider.post<TestRequest, TestResponse>(
            url = "https://api.test.com/items"
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
        
        val step = provider.post<TestRequest, TestResponse>(
            url = "https://api.test.com/items"
        )

        TestUtils.executeAndAssertFailure(step, TestRequest("test data"))
    }

    @Test
    fun `FakeApiProvider should call error mapper correctly`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ ->
            Result.failure(AppFailure.ApiError.TimeoutError)
        }
        
        val step = provider.request<Unit>(
            method = HttpMethod.Get,
            url = "https://api.test.com/test"
        )

        TestUtils.executeAndAssertFailure(step, Unit)
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
            url = "https://api.test.com/test"
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
            url = "https://example.com/api/endpoint"
        )
        
        runBlocking {
            step.process(Unit)
        }
        
        assertEquals(HttpMethod.Post, receivedMethod)
        assertEquals("https://example.com/api/endpoint", receivedUrl)
    }
}
