package app.mcorg.pipeline.auth

import app.mcorg.pipeline.failure.ApiFailure
import app.mcorg.test.utils.TestUtils
import app.mcorg.config.XstsAuthorizationApiConfig
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.auth.domain.TokenData
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals

/**
 * Test suite for GetXstsToken - XSTS authorization token exchange
 *
 * Tests XSTS token exchange with mocked XSTS API:
 * - Successful XSTS token exchange using Xbox Live token
 * - HTTP error scenarios (4xx, 5xx responses)
 * - Network/API failure scenarios
 * - Token data preservation (hash should remain unchanged)
 *
 * Priority: High (Critical for XSTS authorization in authentication chain)
 */
class GetXstsTokenTest {

    private val testTokenData = TokenData("xbox-live-token-123", "user-hash-456")

    @BeforeEach
    fun setup() {
        // Reset API provider to default state before each test
        XstsAuthorizationApiConfig.resetProvider()
    }

    @AfterEach
    fun tearDown() {
        // Clean up after each test
        XstsAuthorizationApiConfig.resetProvider()
    }

    @Test
    fun `should successfully exchange Xbox token for XSTS token`() {
        // Arrange
        val mockResponse = """
            {
                "IssueInstant": "2023-01-01T00:00:00.0000000Z",
                "NotAfter": "2023-01-02T00:00:00.0000000Z",
                "Token": "xsts-authorization-token-789",
                "DisplayClaims": {
                    "xui": [
                        {
                            "uhs": "user-hash-456"
                        }
                    ]
                }
            }
        """.trimIndent()

        XstsAuthorizationApiConfig.useFakeProvider { _, _ ->
            Result.success(mockResponse)
        }

        // Act
        val result = TestUtils.executeAndAssertSuccess(GetXstsToken, testTokenData)

        // Assert
        assertEquals("xsts-authorization-token-789", result.token)
        assertEquals("user-hash-456", result.hash) // Hash should be preserved from input
    }

    @Test
    fun `should preserve user hash from input token data`() {
        // Arrange
        val inputWithDifferentHash = TokenData("xbox-token-abc", "different-hash-xyz")

        val mockResponse = """
            {
                "IssueInstant": "2023-01-01T00:00:00.0000000Z",
                "NotAfter": "2023-01-02T00:00:00.0000000Z",
                "Token": "xsts-token-def",
                "DisplayClaims": {
                    "xui": [
                        {
                            "uhs": "response-hash-should-be-ignored"
                        }
                    ]
                }
            }
        """.trimIndent()

        XstsAuthorizationApiConfig.useFakeProvider { _, _ ->
            Result.success(mockResponse)
        }

        // Act
        val result = TestUtils.executeAndAssertSuccess(GetXstsToken, inputWithDifferentHash)

        // Assert
        assertEquals("xsts-token-def", result.token)
        assertEquals("different-hash-xyz", result.hash) // Should preserve input hash, not response hash
    }

    @Test
    fun `should handle HTTP 401 Unauthorized error from XSTS API`() {
        // Arrange
        XstsAuthorizationApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.Unauthorized.value, "Invalid Xbox Live token")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXstsToken,
            testTokenData,
            MicrosoftSignInFailure.MicrosoftError::class.java
        )
    }

    @Test
    fun `should handle HTTP 400 Bad Request error from XSTS API`() {
        // Arrange
        XstsAuthorizationApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.BadRequest.value, "Malformed XSTS request")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXstsToken,
            testTokenData,
            MicrosoftSignInFailure.MicrosoftError::class.java
        )
    }

    @Test
    fun `should handle HTTP 403 Forbidden error from XSTS API`() {
        // Arrange
        XstsAuthorizationApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.Forbidden.value, "XSTS authorization denied")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXstsToken,
            testTokenData,
            MicrosoftSignInFailure.MicrosoftError::class.java
        )
    }

    @Test
    fun `should handle HTTP 500 Internal Server Error from XSTS API`() {
        // Arrange
        XstsAuthorizationApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.InternalServerError.value, "XSTS service error")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXstsToken,
            testTokenData,
            MicrosoftSignInFailure.MicrosoftError::class.java
        )
    }

    @Test
    fun `should handle network timeout or connection error`() {
        // Arrange
        XstsAuthorizationApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.NetworkError
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXstsToken,
            testTokenData,
            MicrosoftSignInFailure.MicrosoftError::class.java
        )
    }

    @Test
    fun `should handle malformed response from XSTS API`() {
        // Arrange
        XstsAuthorizationApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.SerializationError
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXstsToken,
            testTokenData,
            MicrosoftSignInFailure.MicrosoftError::class.java
        )
    }

    @Test
    fun `should handle empty Xbox token in token data`() {
        // Arrange
        val emptyTokenData = TokenData("", "user-hash-456")

        XstsAuthorizationApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.BadRequest.value, "Empty Xbox token")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXstsToken,
            emptyTokenData,
            MicrosoftSignInFailure.MicrosoftError::class.java
        )
    }
}
