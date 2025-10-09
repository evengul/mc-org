package app.mcorg.config

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.ApiFailure
import app.mcorg.test.utils.TestUtils
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class ApiProviderEdgeCasesTest {

    @Serializable
    data class ComplexResponse(
        val data: List<Item>,
        val metadata: Metadata
    )

    @Serializable
    data class Item(val id: Int, val name: String, val tags: List<String>?)

    @Serializable
    data class Metadata(val total: Int, val hasMore: Boolean)

    sealed class TestError {
        data class HttpError(val status: Int, val body: String?) : TestError()
        data object SerializationError : TestError()
        data object NetworkError : TestError()
        data object TimeoutError : TestError()
        data object RateLimitError : TestError()
        data object UnknownError : TestError()
    }

    private fun detailedErrorMapper(failure: ApiFailure): TestError = when (failure) {
        is ApiFailure.HttpError -> TestError.HttpError(failure.statusCode, failure.body)
        is ApiFailure.SerializationError -> TestError.SerializationError
        is ApiFailure.NetworkError -> TestError.NetworkError
        is ApiFailure.TimeoutError -> TestError.TimeoutError
        is ApiFailure.RateLimitExceeded -> TestError.RateLimitError
        is ApiFailure.UnknownError -> TestError.UnknownError
    }

    @Test
    fun `should handle complex nested JSON structures`() {
        val config = TestApiConfig()
        val complexJson = """
        {
            "data": [
                {"id": 1, "name": "Item 1", "tags": ["tag1", "tag2"]},
                {"id": 2, "name": "Item 2", "tags": null}
            ],
            "metadata": {
                "total": 2,
                "hasMore": false
            }
        }
        """.trimIndent()

        val provider = FakeApiProvider(config) { _, _ -> Result.success(complexJson) }

        val step = provider.get<Unit, TestError, ComplexResponse>(
            url = "https://api.example.com/complex",
            errorMapper = ::detailedErrorMapper
        )

        val response = TestUtils.executeAndAssertSuccess(step, Unit)
        assertEquals(2, response.data.size)
        assertEquals("Item 1", response.data[0].name)
        assertEquals(listOf("tag1", "tag2"), response.data[0].tags)
        assertEquals(null, response.data[1].tags)
        assertEquals(2, response.metadata.total)
        assertEquals(false, response.metadata.hasMore)
    }

    @Test
    fun `should handle JSON with unknown fields gracefully`() {
        val config = TestApiConfig()
        val jsonWithExtraFields = """
        {
            "data": [
                {"id": 1, "name": "Item 1", "tags": ["tag1"], "unknownField": "value"}
            ],
            "metadata": {
                "total": 1,
                "hasMore": false,
                "extraMetadata": {"nested": "data"}
            },
            "apiVersion": "2.0",
            "timestamp": "2024-01-01T00:00:00Z"
        }
        """.trimIndent()

        val provider = FakeApiProvider(config) { _, _ -> Result.success(jsonWithExtraFields) }

        val step = provider.get<Unit, TestError, ComplexResponse>(
            url = "https://api.example.com/with-extra-fields",
            errorMapper = ::detailedErrorMapper
        )

        val response = TestUtils.executeAndAssertSuccess(step, Unit)
        assertEquals(1, response.data.size)
        assertEquals("Item 1", response.data[0].name)
        assertEquals(1, response.metadata.total)
    }

    @Test
    fun `should handle empty JSON arrays and null values`() {
        val config = TestApiConfig()
        val emptyJson = """
        {
            "data": [],
            "metadata": {
                "total": 0,
                "hasMore": false
            }
        }
        """.trimIndent()

        val provider = FakeApiProvider(config) { _, _ -> Result.success(emptyJson) }

        val step = provider.get<Unit, TestError, ComplexResponse>(
            url = "https://api.example.com/empty",
            errorMapper = ::detailedErrorMapper
        )

        val response = TestUtils.executeAndAssertSuccess(step, Unit)
        assertTrue(response.data.isEmpty())
        assertEquals(0, response.metadata.total)
    }

    @Test
    fun `should handle whitespace-only response`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ -> Result.success("   \n\t  ") }

        val step = provider.get<Unit, TestError, ComplexResponse>(
            url = "https://api.example.com/whitespace",
            errorMapper = ::detailedErrorMapper
        )

        TestUtils.executeAndAssertFailure(
            step,
            Unit,
            TestError.SerializationError::class.java
        )
    }

    @Test
    fun `should handle empty string response`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ -> Result.success("") }

        val step = provider.get<Unit, TestError, ComplexResponse>(
            url = "https://api.example.com/empty-string",
            errorMapper = ::detailedErrorMapper
        )

        TestUtils.executeAndAssertFailure(
            step,
            Unit,
            TestError.SerializationError::class.java
        )
    }

    @Test
    fun `should preserve HTTP error details in error mapper`() {
        val config = TestApiConfig()
        val errorBody = """{"error": "Resource not found", "code": "NOT_FOUND"}"""
        val provider = FakeApiProvider(config) { _, _ ->
            Result.failure(ApiFailure.HttpError(404, errorBody))
        }

        val step = provider.get<Unit, TestError, ComplexResponse>(
            url = "https://api.example.com/not-found",
            errorMapper = ::detailedErrorMapper
        )

        val error = TestUtils.executeAndAssertFailure(
            step,
            Unit,
            TestError.HttpError::class.java
        )
        assertIs<TestError.HttpError>(error)
        assertEquals(404, error.status)
        assertEquals(errorBody, error.body)
    }

    @Test
    fun `should handle various HTTP status codes`() {
        val config = TestApiConfig()
        val statusCodes = listOf(400, 401, 403, 404, 429, 500, 502, 503)

        statusCodes.forEach { statusCode ->
            val provider = FakeApiProvider(config) { _, _ ->
                Result.failure(ApiFailure.HttpError(statusCode, "Error $statusCode"))
            }

            val step = provider.get<Unit, TestError, ComplexResponse>(
                url = "https://api.example.com/status/$statusCode",
                errorMapper = ::detailedErrorMapper
            )

            val error = TestUtils.executeAndAssertFailure(
                step,
                Unit,
                TestError.HttpError::class.java
            )
            assertIs<TestError.HttpError>(error)
            assertEquals(statusCode, error.status)
            assertEquals("Error $statusCode", error.body)
        }
    }

    @Test
    fun `should handle custom header and body builders`() {
        val config = TestApiConfig()

        data class TestInput(val token: String, val payload: Map<String, String>)

        val capturedHeaders = mutableMapOf<String, String>()
        var capturedBody: String? = null

        val provider = FakeApiProvider(config) { _, _ ->
            Result.success("""{"data": [], "metadata": {"total": 0, "hasMore": false}}""")
        }

        val step = provider.post<TestInput, TestError, ComplexResponse>(
            url = "https://api.example.com/custom",
            headerBuilder = { builder: HttpRequestBuilder, input: TestInput ->
                builder.header("Authorization", "Bearer ${input.token}")
                builder.header("X-Custom-Header", "custom-value")
                capturedHeaders["Authorization"] = "Bearer ${input.token}"
                capturedHeaders["X-Custom-Header"] = "custom-value"
            },
            bodyBuilder = { _: HttpRequestBuilder, input: TestInput ->
                val bodyContent = input.payload.entries.joinToString(",") { "${it.key}=${it.value}" }
                capturedBody = bodyContent
                // In real implementation, this would set the actual request body
            },
            errorMapper = ::detailedErrorMapper
        )

        val testInput = TestInput(
            token = "test-token-123",
            payload = mapOf("key1" to "value1", "key2" to "value2")
        )

        TestUtils.executeAndAssertSuccess(
            step,
            testInput
        )

        // Verify header builder was called (in mock scenario, we capture the values)
        assertEquals("Bearer test-token-123", capturedHeaders["Authorization"])
        assertEquals("custom-value", capturedHeaders["X-Custom-Header"])

        // Verify body builder was called
        assertNotNull(capturedBody)
        assertTrue(capturedBody.contains("key1=value1"))
        assertTrue(capturedBody.contains("key2=value2"))
    }

    @Test
    fun `should handle all ApiFailure types comprehensively`() {
        val config = TestApiConfig()

        val allFailureTypes = mapOf(
            "network" to ApiFailure.NetworkError,
            "timeout" to ApiFailure.TimeoutError,
            "rate-limit" to ApiFailure.RateLimitExceeded,
            "http-400" to ApiFailure.HttpError(400, "Bad Request"),
            "http-500" to ApiFailure.HttpError(500),
            "serialization" to ApiFailure.SerializationError,
            "unknown" to ApiFailure.UnknownError
        )

        allFailureTypes.forEach { (name, failureType) ->
            val provider = FakeApiProvider(config) { _, _ -> Result.failure(failureType) }

            val step = provider.get<Unit, TestError, ComplexResponse>(
                url = "https://api.example.com/$name",
                errorMapper = ::detailedErrorMapper
            )

            val error = TestUtils.executeAndAssertFailure(
                step,
                Unit,
                TestError::class.java
            )

            when (failureType) {
                is ApiFailure.NetworkError -> assertEquals(TestError.NetworkError, error)
                is ApiFailure.TimeoutError -> assertEquals(TestError.TimeoutError, error)
                is ApiFailure.RateLimitExceeded -> assertEquals(TestError.RateLimitError, error)
                is ApiFailure.HttpError -> {
                    assertIs<TestError.HttpError>(error)
                    assertEquals(failureType.statusCode, error.status)
                    assertEquals(failureType.body, error.body)
                }
                is ApiFailure.SerializationError -> assertEquals(TestError.SerializationError, error)
                is ApiFailure.UnknownError -> assertEquals(TestError.UnknownError, error)
            }
        }
    }
}
