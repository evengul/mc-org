package app.mcorg.config

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.test.utils.TestUtils
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

        val step = provider.get<Unit, ComplexResponse>(
            url = "https://api.example.com/complex",
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

        val step = provider.get<Unit, ComplexResponse>(
            url = "https://api.example.com/with-extra-fields",
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

        val step = provider.get<Unit, ComplexResponse>(
            url = "https://api.example.com/empty"
        )

        val response = TestUtils.executeAndAssertSuccess(step, Unit)
        assertTrue(response.data.isEmpty())
        assertEquals(0, response.metadata.total)
    }

    @Test
    fun `should handle whitespace-only response`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ -> Result.success("   \n\t  ") }

        val step = provider.get<Unit, ComplexResponse>(
            url = "https://api.example.com/whitespace"
        )

        TestUtils.executeAndAssertFailure(
            step,
            Unit
        )
    }

    @Test
    fun `should handle empty string response`() {
        val config = TestApiConfig()
        val provider = FakeApiProvider(config) { _, _ -> Result.success("") }

        val step = provider.get<Unit, ComplexResponse>(
            url = "https://api.example.com/empty-string"
        )

        TestUtils.executeAndAssertFailure(
            step,
            Unit
        )
    }

    @Test
    fun `should preserve HTTP error details in error mapper`() {
        val config = TestApiConfig()
        val errorBody = """{"error": "Resource not found", "code": "NOT_FOUND"}"""
        val provider = FakeApiProvider(config) { _, _ ->
            Result.failure(AppFailure.ApiError.HttpError(404, errorBody))
        }

        val step = provider.get<Unit, ComplexResponse>(
            url = "https://api.example.com/not-found"
        )

        val error = TestUtils.executeAndAssertFailure(
            step,
            Unit
        )
        assertIs<AppFailure.ApiError.HttpError>(error)
        assertEquals(404, error.statusCode)
        assertEquals(errorBody, error.body)
    }

    @Test
    fun `should handle various HTTP status codes`() {
        val config = TestApiConfig()
        val statusCodes = listOf(400, 401, 403, 404, 429, 500, 502, 503)

        statusCodes.forEach { statusCode ->
            val provider = FakeApiProvider(config) { _, _ ->
                Result.failure(AppFailure.ApiError.HttpError(statusCode, "Error $statusCode"))
            }

            val step = provider.get<Unit, ComplexResponse>(
                url = "https://api.example.com/status/$statusCode",
            )

            val error = TestUtils.executeAndAssertFailure(
                step,
                Unit
            )
            assertIs<AppFailure.ApiError.HttpError>(error)
            assertEquals(statusCode, error.statusCode)
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

        val step = provider.post<TestInput, ComplexResponse>(
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
            }
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
}
