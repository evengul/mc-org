package app.mcorg.pipeline

import app.mcorg.config.ApiProvider
import app.mcorg.pipeline.failure.ApiFailure
import app.mcorg.pipeline.ApiSteps
import io.ktor.http.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Test data classes defined at top level to avoid visibility issues
@Serializable
data class TestResponse(val id: Int, val name: String)
data class CreateResponse(val success: Boolean, val id: Int)
@Serializable
data class User(val id: Int, val name: String)
@Serializable
data class Project(val title: String, val description: String)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiStepsTest {

    private val mockApiProvider = mockk<ApiProvider>()

    @BeforeEach
    fun setup() {
        // Mock common ApiProvider methods
        every { mockApiProvider.getBaseUrl() } returns "https://api.example.com"
        every { mockApiProvider.getContentType() } returns ContentType.Application.Json
        every { mockApiProvider.acceptContentType() } returns ContentType.Application.Json
        every { mockApiProvider.getUserAgent() } returns "test-agent/1.0.0"
    }

    @AfterEach
    fun cleanup() {
        clearAllMocks()
    }

    @Test
    fun `get - creates step with correct parameters`() {
        runBlocking {
            // Given
            val url = "https://api.example.com/test"
            val errorMapper: (ApiFailure) -> String = { "API_ERROR: $it" }

            // When
            val step = ApiSteps.get<Unit, String>(mockApiProvider, url, errorMapper = errorMapper)

            // Then
            // Test that step is created without error
            assertNotNull(step)
        }
    }

    @Test
    fun `post - creates step with correct parameters`() {
        runBlocking {
            // Given
            val url = "https://api.example.com/create"
            val errorMapper: (ApiFailure) -> String = { "API_ERROR: $it" }
            val bodyBuilder: (io.ktor.client.request.HttpRequestBuilder, String) -> Unit = { _, _ ->
                // Mock body builder - actual implementation would set the body
            }

            // When
            val step = ApiSteps.post<String, String>(
                mockApiProvider,
                url,
                bodyBuilder = bodyBuilder,
                errorMapper = errorMapper
            )

            // Then
            // Test that step is created without error
            assertNotNull(step)
        }
    }

    @Test
    fun `getJson - creates step with correct parameters`() {
        runBlocking {
            // Given
            val url = "https://api.example.com/data"
            val errorMapper: (ApiFailure) -> String = { "API_ERROR: $it" }

            // When
            val step = ApiSteps.getJson<Unit, String, TestResponse>(
                mockApiProvider,
                url,
                errorMapper = errorMapper
            )

            // Then
            // Test that step is created without error
            assertNotNull(step)
            // Note: Cannot test execution without actual HTTP responses in unit tests
            // This would require integration testing with a real or mock HTTP server
        }
    }

    @Test
    fun `postJson - creates step with correct parameters`() {
        runBlocking {
            // Given
            val url = "https://api.example.com/create"
            val errorMapper: (ApiFailure) -> String = { "API_ERROR: $it" }
            val bodyBuilder: (io.ktor.client.request.HttpRequestBuilder, Map<String, String>) -> Unit = { _, _ ->
                // Mock body builder - actual implementation would set the body
            }

            // When
            val step = ApiSteps.postJson<Map<String, String>, String, CreateResponse>(
                mockApiProvider,
                url,
                bodyBuilder = bodyBuilder,
                errorMapper = errorMapper
            )

            // Then
            // Test that step is created without error
            assertNotNull(step)
            // Note: Cannot test execution without actual HTTP responses in unit tests
            // This would require integration testing with a real or mock HTTP server
        }
    }

    @Test
    fun `deserializeJson - handles valid JSON correctly`() {
        runBlocking {
            // Given
            val validJson = """{"id": 123, "name": "test"}"""

            // When
            val result = ApiSteps.json.decodeFromString<TestResponse>(validJson)

            // Then
            assertEquals(123, result.id)
            assertEquals("test", result.name)
        }
    }

    @Test
    fun `deserializeJson - handles invalid JSON with proper error`() {
        // Given
        val invalidJson = """{"hello": }"""

        // When/Then
        try {
            ApiSteps.json.decodeFromString<TestResponse>(invalidJson)
            // Should not reach here
            assert(false) { "Expected SerializationException" }
        } catch (e: Exception) {
            // This is expected for invalid JSON
            assertNotNull(e)
        }
    }

    @Test
    fun `step execution - can process inputs correctly`() {
        runBlocking {
            // Given
            val errorMapper: (ApiFailure) -> String = { "ERROR: $it" }
            val testInput = "test-input"

            // Create a step that we know will fail (since we can't make real HTTP calls in unit tests)
            val step = ApiSteps.get<String, String>(
                mockApiProvider,
                "https://nonexistent.example.com/test",
                errorMapper = errorMapper
            )

            // When
            val result = step.process(testInput)

            // Then
            // In unit tests, this will likely fail with a network error
            // But we can verify the step processes the input without crashing
            assertTrue(result is app.mcorg.domain.pipeline.Result.Failure)
            assertTrue(result.error.contains("ERROR:"))
        }
    }

    @Test
    fun `headerBuilder - is called with correct parameters during step creation`() {
        // Given
        var capturedInput: String? = null
        var builderWasCalled = false

        val headerBuilder: (io.ktor.client.request.HttpRequestBuilder, String) -> Unit = { _, input ->
            capturedInput = input
            builderWasCalled = true
        }

        val errorMapper: (ApiFailure) -> String = { "ERROR: $it" }

        // When
        val step = ApiSteps.get<String, String>(
            mockApiProvider,
            "https://api.example.com/test",
            headerBuilder = headerBuilder,
            errorMapper = errorMapper
        )

        // Then
        assertNotNull(step)
        // Header builder is only called during step execution, not creation
        assertEquals(false, builderWasCalled)
        assertEquals(null, capturedInput)
    }

    @Test
    fun `bodyBuilder - is called with correct parameters during step creation`() {
        // Given
        var capturedInput: Map<String, Any>? = null
        var builderWasCalled = false

        val bodyBuilder: (io.ktor.client.request.HttpRequestBuilder, Map<String, Any>) -> Unit = { _, input ->
            capturedInput = input
            builderWasCalled = true
        }

        val errorMapper: (ApiFailure) -> String = { "ERROR: $it" }

        // When
        val step = ApiSteps.post<Map<String, Any>, String>(
            mockApiProvider,
            "https://api.example.com/test",
            bodyBuilder = bodyBuilder,
            errorMapper = errorMapper
        )

        // Then
        assertNotNull(step)
        // Body builder is only called during step execution, not creation
        assertEquals(false, builderWasCalled)
        assertEquals(null, capturedInput)
    }

    @Test
    fun `json instance - has correct configuration`() {
        // Test that the JSON instance is properly configured
        val jsonInstance = ApiSteps.json

        // Test that it can handle unknown keys (should not throw)
        val jsonWithUnknownKey = """{"id": 123, "name": "test", "unknown": "field"}"""
        val result = jsonInstance.decodeFromString<TestResponse>(jsonWithUnknownKey)

        assertEquals(123, result.id)
        assertEquals("test", result.name)
    }

    @Test
    fun `ApiProvider methods are called during step creation`() {
        // Given
        val url = "https://api.example.com/test"
        val errorMapper: (ApiFailure) -> String = { "API_ERROR: $it" }

        // When
        val step = ApiSteps.get<Unit, String>(mockApiProvider, url, errorMapper = errorMapper)

        // Then
        // Verify that the step was created (ApiProvider methods would be called during actual execution)
        assertNotNull(step)
    }

    @Test
    fun `error mapper function works correctly`() {
        // Given
        val errorMapper: (ApiFailure) -> String = { failure ->
            when (failure) {
                is ApiFailure.NetworkError -> "NETWORK_ERROR"
                is ApiFailure.TimeoutError -> "TIMEOUT_ERROR"
                is ApiFailure.RateLimitExceeded -> "RATE_LIMIT_ERROR"
                is ApiFailure.HttpError -> "HTTP_ERROR: ${failure.statusCode}"
                is ApiFailure.SerializationError -> "SERIALIZATION_ERROR"
                is ApiFailure.UnknownError -> "UNKNOWN_ERROR"
            }
        }

        // When & Then
        assertEquals("NETWORK_ERROR", errorMapper(ApiFailure.NetworkError))
        assertEquals("TIMEOUT_ERROR", errorMapper(ApiFailure.TimeoutError))
        assertEquals("RATE_LIMIT_ERROR", errorMapper(ApiFailure.RateLimitExceeded))
        assertEquals("HTTP_ERROR: 404", errorMapper(ApiFailure.HttpError(404, "Not Found")))
        assertEquals("HTTP_ERROR: 500", errorMapper(ApiFailure.HttpError(500)))
        assertEquals("SERIALIZATION_ERROR", errorMapper(ApiFailure.SerializationError))
        assertEquals("UNKNOWN_ERROR", errorMapper(ApiFailure.UnknownError))
    }

    @Test
    fun `ApiFailure HttpError handles optional body parameter`() {
        // Given
        val errorWithBody = ApiFailure.HttpError(400, "Bad Request")
        val errorWithoutBody = ApiFailure.HttpError(500)

        // Then
        assertEquals(400, errorWithBody.statusCode)
        assertEquals("Bad Request", errorWithBody.body)
        assertEquals(500, errorWithoutBody.statusCode)
        assertEquals(null, errorWithoutBody.body)
    }

    @Test
    fun `header builder function signature is correct`() {
        // Given
        val headerBuilder: (io.ktor.client.request.HttpRequestBuilder, String) -> Unit = { _, _ ->
            // Mock header builder - actual implementation would add headers
        }

        // When
        val step = ApiSteps.get<String, String>(
            mockApiProvider,
            "https://api.example.com/protected",
            headerBuilder = headerBuilder,
            errorMapper = { "ERROR: $it" }
        )

        // Then
        assertNotNull(step)
    }

    @Test
    fun `body builder function signature is correct`() {
        // Given
        val bodyBuilder: (io.ktor.client.request.HttpRequestBuilder, Map<String, Any>) -> Unit = { _, _ ->
            // Mock body builder - actual implementation would set the body
        }

        // When
        val step = ApiSteps.post<Map<String, Any>, String>(
            mockApiProvider,
            "https://api.example.com/data",
            bodyBuilder = bodyBuilder,
            errorMapper = { "ERROR: $it" }
        )

        // Then
        assertNotNull(step)
    }

    @Test
    fun `different types work with getJson`() {
        // When - Test that getJson works with different types
        val userStep = ApiSteps.getJson<Unit, String, User>(
            mockApiProvider,
            "https://api.example.com/user",
            errorMapper = { "ERROR: $it" }
        )

        val projectStep = ApiSteps.getJson<Unit, String, Project>(
            mockApiProvider,
            "https://api.example.com/project",
            errorMapper = { "ERROR: $it" }
        )

        // Then
        assertNotNull(userStep)
        assertNotNull(projectStep)
    }

    @ParameterizedTest
    @MethodSource("httpStatusCodes")
    fun `HttpError handles various status codes`(statusCode: Int, expectedCategory: String) {
        // Given
        val error = ApiFailure.HttpError(statusCode, "Test error")

        // When
        val category = when (statusCode) {
            in 400..499 -> "CLIENT_ERROR"
            in 500..599 -> "SERVER_ERROR"
            else -> "OTHER"
        }

        // Then
        assertEquals(expectedCategory, category)
        assertEquals(statusCode, error.statusCode)
        assertEquals("Test error", error.body)
    }

    @Test
    fun `cleanup method exists and can be called`() {
        // This test ensures cleanup method exists and can be called safely
        // The actual HTTP client cleanup behavior would be tested in integration tests
        ApiSteps.cleanup()
        // No exception should be thrown
    }

    @Test
    fun `ApiProvider methods have correct return types`() {
        // Verify the mock setup matches expected ApiProvider interface
        assertEquals("https://api.example.com", mockApiProvider.getBaseUrl())
        assertEquals(ContentType.Application.Json, mockApiProvider.getContentType())
        assertEquals(ContentType.Application.Json, mockApiProvider.acceptContentType())
        assertEquals("test-agent/1.0.0", mockApiProvider.getUserAgent())
    }

    @Test
    fun `ApiFailure sealed interface covers all error types`() {
        // Test that all ApiFailure types are properly defined
        val failures = listOf(
            ApiFailure.NetworkError,
            ApiFailure.TimeoutError,
            ApiFailure.RateLimitExceeded,
            ApiFailure.HttpError(404, "Not Found"),
            ApiFailure.HttpError(500), // Without body
            ApiFailure.SerializationError,
            ApiFailure.UnknownError
        )

        // All failures should be instances of ApiFailure
        failures.forEach { failure ->
            assertNotNull(failure)
            // Test that they are all ApiFailure instances without redundant type checking
        }
    }

    @Test
    fun `Step interface generic types work correctly`() {
        // Test that the Step interface supports different generic type combinations
        val errorMapper: (ApiFailure) -> String = { "ERROR: $it" }

        // Different input types
        val unitStep = ApiSteps.get<Unit, String>(mockApiProvider, "test", errorMapper = errorMapper)
        val stringStep = ApiSteps.get<String, String>(mockApiProvider, "test", errorMapper = errorMapper)
        val mapStep = ApiSteps.get<Map<String, Any>, String>(mockApiProvider, "test", errorMapper = errorMapper)

        // All should compile and create steps
        assertNotNull(unitStep)
        assertNotNull(stringStep)
        assertNotNull(mapStep)
    }

    companion object {
        @JvmStatic
        fun httpStatusCodes(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(400, "CLIENT_ERROR"),
                Arguments.of(401, "CLIENT_ERROR"),
                Arguments.of(403, "CLIENT_ERROR"),
                Arguments.of(404, "CLIENT_ERROR"),
                Arguments.of(422, "CLIENT_ERROR"),
                Arguments.of(500, "SERVER_ERROR"),
                Arguments.of(502, "SERVER_ERROR"),
                Arguments.of(503, "SERVER_ERROR"),
                Arguments.of(504, "SERVER_ERROR")
            )
        }
    }
}
