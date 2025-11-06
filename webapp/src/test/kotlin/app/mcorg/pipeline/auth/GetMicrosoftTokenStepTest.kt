package app.mcorg.pipeline.auth

import app.mcorg.config.MicrosoftLoginApiConfig
import app.mcorg.domain.Local
import app.mcorg.domain.Production
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.GetMicrosoftTokenFailure
import app.mcorg.test.utils.TestUtils
import io.ktor.http.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test suite for GetMicrosoftTokenStep - Microsoft OAuth token exchange
 *
 * Tests Microsoft token exchange with mocked Microsoft API:
 * - Successful token exchange with valid authorization code
 * - HTTP error scenarios (4xx, 5xx responses)
 * - Network/API failure scenarios
 * - Environment-specific redirect URL handling
 *
 * Priority: High (Critical for OAuth authentication flow)
 */
class GetMicrosoftTokenStepTest {

    private val testClientId = "test-client-id"
    private val testClientSecret = "test-client-secret"
    private val testCode = "test-oauth-code"

    @BeforeEach
    fun setup() {
        // Reset API provider to default state before each test
        MicrosoftLoginApiConfig.resetProvider()
    }

    @AfterEach
    fun tearDown() {
        // Clean up after each test
        MicrosoftLoginApiConfig.resetProvider()
    }

    @Test
    fun `should successfully exchange code for access token in Local environment`() {
        // Arrange
        val input = GetMicrosoftTokenInput(
            code = testCode,
            clientId = testClientId,
            clientSecret = testClientSecret,
            env = Local,
            host = null
        )

        val mockResponse = """
            {
                "token_type": "Bearer",
                "scope": "XboxLive.signin offline_access",
                "expires_in": 3600,
                "ext_expires_in": 3600,
                "access_token": "test-access-token-123",
                "id_token": "test-id-token-456"
            }
        """.trimIndent()

        MicrosoftLoginApiConfig.useFakeProvider { _, _ -> Result.success(mockResponse) }

        // Act
        val result = TestUtils.executeAndAssertSuccess(GetMicrosoftTokenStep, input)

        // Assert
        assertEquals("test-access-token-123", result)
    }

    @Test
    fun `should successfully exchange code for access token in Production environment with host`() {
        // Arrange
        val input = GetMicrosoftTokenInput(
            code = testCode,
            clientId = testClientId,
            clientSecret = testClientSecret,
            env = Production,
            host = "mcorg.app"
        )

        val mockResponse = """
            {
                "token_type": "Bearer",
                "scope": "XboxLive.signin offline_access",
                "expires_in": 7200,
                "ext_expires_in": 7200,
                "access_token": "prod-access-token-789",
                "id_token": "prod-id-token-012"
            }
        """.trimIndent()

        MicrosoftLoginApiConfig.useFakeProvider { _, _ ->
            Result.success(mockResponse)
        }

        // Act
        val result = TestUtils.executeAndAssertSuccess(GetMicrosoftTokenStep, input)

        // Assert
        assertEquals("prod-access-token-789", result)
    }

    @Test
    fun `should fail when Production environment has no host`() {
        // Arrange
        val input = GetMicrosoftTokenInput(
            code = testCode,
            clientId = testClientId,
            clientSecret = testClientSecret,
            env = Production,
            host = null
        )

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMicrosoftTokenStep,
            input,
            GetMicrosoftTokenFailure.NoHostForNonLocalEnv::class.java
        )
    }

    @Test
    fun `should handle HTTP 400 Bad Request error from Microsoft API`() {
        // Arrange
        val input = GetMicrosoftTokenInput(
            code = "invalid-code",
            clientId = testClientId,
            clientSecret = testClientSecret,
            env = Local,
            host = null
        )

        MicrosoftLoginApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.BadRequest.value, "Invalid authorization code")
            )
        }

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            GetMicrosoftTokenStep,
            input,
            GetMicrosoftTokenFailure.CouldNotGetToken::class.java
        )
        when(error) {
            is GetMicrosoftTokenFailure.CouldNotGetToken -> {
                assertEquals("http_error", error.error)
                assertEquals("HTTP 400", error.description)
            }
            else -> throw IllegalStateException("Unexpected error type: ${error::class.java}")
        }
    }

    @Test
    fun `should handle HTTP 401 Unauthorized error from Microsoft API`() {
        // Arrange
        val input = GetMicrosoftTokenInput(
            code = testCode,
            clientId = "invalid-client-id",
            clientSecret = testClientSecret,
            env = Local,
            host = null
        )

        MicrosoftLoginApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.Unauthorized.value, "Invalid client credentials")
            )
        }

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            GetMicrosoftTokenStep,
            input,
            GetMicrosoftTokenFailure.CouldNotGetToken::class.java
        )
        when(error) {
            is GetMicrosoftTokenFailure.CouldNotGetToken -> {
                assertEquals("http_error", error.error)
                assertEquals("HTTP 401", error.description)
            }
            else -> throw IllegalStateException("Unexpected error type: ${error::class.java}")
        }
    }

    @Test
    fun `should handle HTTP 500 Internal Server Error from Microsoft API`() {
        // Arrange
        val input = GetMicrosoftTokenInput(
            code = testCode,
            clientId = testClientId,
            clientSecret = testClientSecret,
            env = Local,
            host = null
        )

        MicrosoftLoginApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.InternalServerError.value, "Microsoft server error")
            )
        }

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            GetMicrosoftTokenStep,
            input,
            GetMicrosoftTokenFailure.CouldNotGetToken::class.java
        )
        when(error) {
            is GetMicrosoftTokenFailure.CouldNotGetToken -> {
                assertEquals("http_error", error.error)
                assertEquals("HTTP 500", error.description)
            }
            else -> throw IllegalStateException("Unexpected error type: ${error::class.java}")
        }
    }

    @Test
    fun `should handle network timeout or connection error`() {
        // Arrange
        val input = GetMicrosoftTokenInput(
            code = testCode,
            clientId = testClientId,
            clientSecret = testClientSecret,
            env = Local,
            host = null
        )

        MicrosoftLoginApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.NetworkError
            )
        }

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            GetMicrosoftTokenStep,
            input,
            GetMicrosoftTokenFailure.CouldNotGetToken::class.java
        )
        when(error) {
            is GetMicrosoftTokenFailure.CouldNotGetToken -> {
                assertEquals("api_error", error.error)
                assertEquals("NetworkError", error.description)
            }
            else -> throw IllegalStateException("Unexpected error type: ${error::class.java}")
        }
    }

    @Test
    fun `should handle malformed response from Microsoft API`() {
        // Arrange
        val input = GetMicrosoftTokenInput(
            code = testCode,
            clientId = testClientId,
            clientSecret = testClientSecret,
            env = Local,
            host = null
        )

        MicrosoftLoginApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.SerializationError
            )
        }

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            GetMicrosoftTokenStep,
            input,
            GetMicrosoftTokenFailure.CouldNotGetToken::class.java
        )
        when(error) {
            is GetMicrosoftTokenFailure.CouldNotGetToken -> {
                assertEquals("api_error", error.error)
                assertEquals("SerializationError", error.description)
            }
            else -> throw IllegalStateException("Unexpected error type: ${error::class.java}")
        }
    }
}
