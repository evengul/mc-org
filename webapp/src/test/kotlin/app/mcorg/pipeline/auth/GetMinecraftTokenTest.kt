package app.mcorg.pipeline.auth

import app.mcorg.pipeline.failure.GetMinecraftTokenFailure
import app.mcorg.pipeline.failure.ApiFailure
import app.mcorg.test.utils.TestUtils
import app.mcorg.config.MinecraftApiConfig
import app.mcorg.domain.pipeline.Result
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals

/**
 * Test suite for GetMinecraftToken - Minecraft access token exchange
 *
 * Tests Minecraft token exchange with mocked Minecraft API:
 * - Successful Minecraft token exchange using XSTS token
 * - HTTP error scenarios (4xx, 5xx responses)
 * - Network/API failure scenarios
 * - Invalid XSTS token handling
 *
 * Priority: High (Critical for final Minecraft authentication step)
 */
class GetMinecraftTokenTest {

    private val testTokenData = TokenData("xsts-token-123", "user-hash-456")

    @BeforeEach
    fun setup() {
        // Reset API provider to default state before each test
        MinecraftApiConfig.resetProvider()
    }

    @AfterEach
    fun tearDown() {
        // Clean up after each test
        MinecraftApiConfig.resetProvider()
    }

    @Test
    fun `should successfully exchange XSTS token for Minecraft access token`() {
        // Arrange
        val mockResponse = """
            {
                "username": "TestPlayer",
                "access_token": "minecraft-access-token-789",
                "token_type": "Bearer",
                "expires_in": 86400
            }
        """.trimIndent()

        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.success(mockResponse)
        }

        // Act
        val result = TestUtils.executeAndAssertSuccess(GetMinecraftToken, testTokenData)

        // Assert
        assertEquals("minecraft-access-token-789", result)
    }

    @Test
    fun `should successfully handle different Minecraft token format`() {
        // Arrange
        val mockResponse = """
            {
                "username": "AnotherPlayer",
                "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.example.token",
                "token_type": "Bearer",
                "expires_in": 43200
            }
        """.trimIndent()

        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.success(mockResponse)
        }

        // Act
        val result = TestUtils.executeAndAssertSuccess(GetMinecraftToken, testTokenData)

        // Assert
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.example.token", result)
    }

    @Test
    fun `should handle HTTP 401 Unauthorized error from Minecraft API`() {
        // Arrange
        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.Unauthorized.value, "Invalid XSTS token")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftToken,
            testTokenData,
            GetMinecraftTokenFailure.CouldNotGetMinecraftToken::class.java
        )
    }

    @Test
    fun `should handle HTTP 400 Bad Request error from Minecraft API`() {
        // Arrange
        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.BadRequest.value, "Malformed Minecraft authentication request")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftToken,
            testTokenData,
            GetMinecraftTokenFailure.CouldNotGetMinecraftToken::class.java
        )
    }

    @Test
    fun `should handle HTTP 403 Forbidden error from Minecraft API`() {
        // Arrange
        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.Forbidden.value, "Minecraft account access denied")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftToken,
            testTokenData,
            GetMinecraftTokenFailure.CouldNotGetMinecraftToken::class.java
        )
    }

    @Test
    fun `should handle HTTP 500 Internal Server Error from Minecraft API`() {
        // Arrange
        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.InternalServerError.value, "Minecraft service error")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftToken,
            testTokenData,
            GetMinecraftTokenFailure.CouldNotGetMinecraftToken::class.java
        )
    }

    @Test
    fun `should handle network timeout or connection error`() {
        // Arrange
        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.NetworkError
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftToken,
            testTokenData,
            GetMinecraftTokenFailure.CouldNotGetMinecraftToken::class.java
        )
    }

    @Test
    fun `should handle malformed response from Minecraft API`() {
        // Arrange
        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.SerializationError
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftToken,
            testTokenData,
            GetMinecraftTokenFailure.CouldNotGetMinecraftToken::class.java
        )
    }

    @Test
    fun `should handle empty XSTS token in token data`() {
        // Arrange
        val emptyTokenData = TokenData("", "user-hash-456")

        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.BadRequest.value, "Empty XSTS token")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftToken,
            emptyTokenData,
            GetMinecraftTokenFailure.CouldNotGetMinecraftToken::class.java
        )
    }

    @Test
    fun `should handle invalid user hash in token data`() {
        // Arrange
        val invalidTokenData = TokenData("xsts-token-123", "")

        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                ApiFailure.HttpError(HttpStatusCode.BadRequest.value, "Invalid user hash")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftToken,
            invalidTokenData,
            GetMinecraftTokenFailure.CouldNotGetMinecraftToken::class.java
        )
    }
}
