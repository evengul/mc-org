package app.mcorg.config

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.ApiFailure
import io.ktor.http.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultApiProviderTest {

    sealed class TestError {
        data object NetworkFailure : TestError()
        data object SerializationFailure : TestError()
        data object RateLimit : TestError()
        data object Timeout : TestError()
        data object HttpFailure : TestError()
        data object Unknown : TestError()
    }

    private fun errorMapper(failure: ApiFailure): TestError = when (failure) {
        is ApiFailure.NetworkError -> TestError.NetworkFailure
        is ApiFailure.TimeoutError -> TestError.Timeout
        is ApiFailure.RateLimitExceeded -> TestError.RateLimit
        is ApiFailure.HttpError -> TestError.HttpFailure
        is ApiFailure.SerializationError -> TestError.SerializationFailure
        is ApiFailure.UnknownError -> TestError.Unknown
    }

    @Test
    fun `error mapping should work correctly for different failure types`() {
        // Test that different ApiFailure types are correctly mapped to TestError types

        assertEquals(TestError.NetworkFailure, errorMapper(ApiFailure.NetworkError))
        assertEquals(TestError.Timeout, errorMapper(ApiFailure.TimeoutError))
        assertEquals(TestError.RateLimit, errorMapper(ApiFailure.RateLimitExceeded))
        assertEquals(TestError.HttpFailure, errorMapper(ApiFailure.HttpError(404, "Not Found")))
        assertEquals(TestError.SerializationFailure, errorMapper(ApiFailure.SerializationError))
        assertEquals(TestError.Unknown, errorMapper(ApiFailure.UnknownError))
    }

    @Test
    fun `HTTP error should include status code and body`() {
        val httpError = ApiFailure.HttpError(400, "Bad Request")

        assertEquals(400, httpError.statusCode)
        assertEquals("Bad Request", httpError.body)
    }

    @Test
    fun `HTTP error can have null body`() {
        val httpError = ApiFailure.HttpError(500)

        assertEquals(500, httpError.statusCode)
        assertEquals(null, httpError.body)
    }

    @Test
    fun `provider type switching should work correctly`() {
        val config = TestApiConfig()

        // Default provider type
        assertEquals(ApiConfig.ProviderType.DEFAULT, config.provider)

        // Switch to fake provider
        config.useFakeProvider { _, _ -> Result.success("fake response") }
        assertEquals(ApiConfig.ProviderType.FAKE, config.provider)
        assertTrue(config.fakeResponses != null)

        // Reset to default
        config.resetProvider()
        assertEquals(ApiConfig.ProviderType.DEFAULT, config.provider)
        assertEquals(null, config.fakeResponses)
    }

    @Test
    fun `fake provider configuration should store response function`() {
        val config = TestApiConfig()
        val responseFunction: (HttpMethod, String) -> Result<ApiFailure, String> = { _, _ ->
            Result.success("test response")
        }

        config.useFakeProvider(responseFunction)

        assertEquals(ApiConfig.ProviderType.FAKE, config.provider)
        assertIs<Function2<HttpMethod, String, Result<ApiFailure, String>>>(config.fakeResponses)

        // Test that the stored function works
        val result = config.fakeResponses!!(HttpMethod.Get, "test-url")
        assertIs<Result.Success<String>>(result)
        assertEquals("test response", result.value)
    }
}
