package app.mcorg.pipeline.auth

import app.mcorg.pipeline.failure.GetXboxProfileFailure
import app.mcorg.pipeline.failure.ApiFailure
import app.mcorg.test.utils.TestUtils
import app.mcorg.config.XboxAuthApiConfig
import app.mcorg.domain.pipeline.Result
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals

/**
 * Test suite for GetXboxProfileStep - Xbox Live authentication
 *
 * Tests Xbox Live profile retrieval with mocked Xbox API:
 * - Successful Xbox token exchange using Microsoft access token
 * - HTTP error scenarios (4xx, 5xx responses)
 * - Network/API failure scenarios
 * - Token validation and user hash extraction
 *
 * Priority: High (Critical for Xbox Live authentication chain)
 */
class GetXboxProfileStepTest {

    private val testAccessToken = "test-microsoft-access-token"

    @BeforeEach
    fun setup() {
        // Reset API provider to default state before each test
        XboxAuthApiConfig.resetProvider()
    }

    @AfterEach
    fun tearDown() {
        // Clean up after each test
        XboxAuthApiConfig.resetProvider()
    }

    @Test
    fun `should successfully get Xbox profile with valid access token`() {
        // Arrange
        val mockResponse = """
            {
                "IssueInstant": "2023-01-01T00:00:00.0000000Z",
                "NotAfter": "2023-01-02T00:00:00.0000000Z",
                "Token": "xbox-live-token-123",
                "DisplayClaims": {
                    "xui": [
                        {
                            "uhs": "user-hash-456"
                        }
                    ]
                }
            }
        """.trimIndent()

        XboxAuthApiConfig.useFakeProvider { _, _ ->
            Result.success(mockResponse)
        }

        // Act
        val result = TestUtils.executeAndAssertSuccess(GetXboxProfileStep, testAccessToken)

        // Assert
        assertEquals("xbox-live-token-123", result.token)
        assertEquals("user-hash-456", result.hash)
    }

    @Test
    fun `should successfully handle Xbox response with different token format`() {
        // Arrange
        val mockResponse = """
            {
                "IssueInstant": "2023-06-15T10:30:00.0000000Z",
                "NotAfter": "2023-06-16T10:30:00.0000000Z",
                "Token": "EwAoA8l6BAAAM4s-VmpxJjn9SVrjk4Wi7D",
                "DisplayClaims": {
                    "xui": [
                        {
                            "uhs": "2533274790395904"
                        }
                    ]
                }
            }
        """.trimIndent()

        XboxAuthApiConfig.useFakeProvider { _, _ ->
            Result.success(mockResponse)
        }

        // Act
        val result = TestUtils.executeAndAssertSuccess(GetXboxProfileStep, testAccessToken)

        // Assert
        assertEquals("EwAoA8l6BAAAM4s-VmpxJjn9SVrjk4Wi7D", result.token)
        assertEquals("2533274790395904", result.hash)
    }

    @Test
    fun `should handle HTTP 401 Unauthorized error from Xbox API`() {
        // Arrange
        XboxAuthApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.Unauthorized.value, "Invalid Microsoft access token")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXboxProfileStep,
            testAccessToken,
            GetXboxProfileFailure.CouldNotGetXboxProfile::class.java
        )
    }

    @Test
    fun `should handle HTTP 400 Bad Request error from Xbox API`() {
        // Arrange
        XboxAuthApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.BadRequest.value, "Malformed request")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXboxProfileStep,
            testAccessToken,
            GetXboxProfileFailure.CouldNotGetXboxProfile::class.java
        )
    }

    @Test
    fun `should handle HTTP 500 Internal Server Error from Xbox API`() {
        // Arrange
        XboxAuthApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.InternalServerError.value, "Xbox service error")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXboxProfileStep,
            testAccessToken,
            GetXboxProfileFailure.CouldNotGetXboxProfile::class.java
        )
    }

    @Test
    fun `should handle network timeout or connection error`() {
        // Arrange
        XboxAuthApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.NetworkError
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXboxProfileStep,
            testAccessToken,
            GetXboxProfileFailure.CouldNotGetXboxProfile::class.java
        )
    }

    @Test
    fun `should handle malformed response from Xbox API`() {
        // Arrange
        XboxAuthApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.SerializationError
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXboxProfileStep,
            testAccessToken,
            GetXboxProfileFailure.CouldNotGetXboxProfile::class.java
        )
    }

    @Test
    fun `should handle empty access token`() {
        // Arrange
        val emptyToken = ""

        XboxAuthApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.BadRequest.value, "Empty access token")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXboxProfileStep,
            emptyToken,
            GetXboxProfileFailure.CouldNotGetXboxProfile::class.java
        )
    }

    @Test
    fun `should handle expired Microsoft access token`() {
        // Arrange
        val expiredToken = "expired-microsoft-token"

        XboxAuthApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.Unauthorized.value, "Token expired")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetXboxProfileStep,
            expiredToken,
            GetXboxProfileFailure.CouldNotGetXboxProfile::class.java
        )
    }
}
