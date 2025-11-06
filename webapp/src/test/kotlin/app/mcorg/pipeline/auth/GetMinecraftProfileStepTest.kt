package app.mcorg.pipeline.auth

import app.mcorg.config.MinecraftApiConfig
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.test.utils.TestUtils
import io.ktor.http.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test suite for GetMinecraftProfileStep - Minecraft profile retrieval
 *
 * Tests Minecraft profile retrieval with mocked Minecraft API:
 * - Successful profile retrieval using Minecraft access token
 * - HTTP error scenarios (4xx, 5xx responses)
 * - Network/API failure scenarios
 * - Profile data validation (username, UUID)
 *
 * Priority: High (Critical for obtaining user's Minecraft profile data)
 */
class GetMinecraftProfileStepTest {

    private val testAccessToken = "minecraft-access-token-123"

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
    fun `should successfully get Minecraft profile with valid access token`() {
        // Arrange
        val mockResponse = """
            {
                "id": "550e8400e29b41d4a716446655440000",
                "name": "TestPlayer"
            }
        """.trimIndent()

        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.success(mockResponse)
        }

        // Act
        val result = TestUtils.executeAndAssertSuccess(GetMinecraftProfileStep, testAccessToken)

        // Assert
        assertEquals("550e8400e29b41d4a716446655440000", result.uuid)
        assertEquals("TestPlayer", result.username)
    }

    @Test
    fun `should successfully handle different username format`() {
        // Arrange
        val mockResponse = """
            {
                "id": "12345678901234567890123456789012",
                "name": "Player_With_Underscores"
            }
        """.trimIndent()

        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.success(mockResponse)
        }

        // Act
        val result = TestUtils.executeAndAssertSuccess(GetMinecraftProfileStep, testAccessToken)

        // Assert
        assertEquals("12345678901234567890123456789012", result.uuid)
        assertEquals("Player_With_Underscores", result.username)
    }

    @Test
    fun `should successfully handle UUID with hyphens removed`() {
        // Arrange
        val mockResponse = """
            {
                "id": "550e8400e29b41d4a716446655440000",
                "name": "AnotherPlayer"
            }
        """.trimIndent()

        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.success(mockResponse)
        }

        // Act
        val result = TestUtils.executeAndAssertSuccess(GetMinecraftProfileStep, testAccessToken)

        // Assert
        assertEquals("550e8400e29b41d4a716446655440000", result.uuid)
        assertEquals("AnotherPlayer", result.username)
    }

    @Test
    fun `should handle HTTP 401 Unauthorized error from Minecraft API`() {
        // Arrange
        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                AppFailure.ApiError.HttpError(HttpStatusCode.Unauthorized.value, "Invalid Minecraft access token")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftProfileStep,
            testAccessToken
        )
    }

    @Test
    fun `should handle HTTP 403 Forbidden error from Minecraft API`() {
        // Arrange
        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                AppFailure.ApiError.HttpError(HttpStatusCode.Forbidden.value, "No Minecraft license")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftProfileStep,
            testAccessToken
        )
    }

    @Test
    fun `should handle HTTP 404 Not Found error from Minecraft API`() {
        // Arrange
        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                AppFailure.ApiError.HttpError(HttpStatusCode.NotFound.value, "Minecraft profile not found")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftProfileStep,
            testAccessToken
        )
    }

    @Test
    fun `should handle HTTP 500 Internal Server Error from Minecraft API`() {
        // Arrange
        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                AppFailure.ApiError.HttpError(HttpStatusCode.InternalServerError.value, "Minecraft service error")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftProfileStep,
            testAccessToken
        )
    }

    @Test
    fun `should handle network timeout or connection error`() {
        // Arrange
        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                AppFailure.ApiError.NetworkError
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftProfileStep,
            testAccessToken
        )
    }

    @Test
    fun `should handle malformed response from Minecraft API`() {
        // Arrange
        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                AppFailure.ApiError.SerializationError
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftProfileStep,
            testAccessToken
        )
    }

    @Test
    fun `should handle empty access token`() {
        // Arrange
        val emptyToken = ""

        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                AppFailure.ApiError.HttpError(HttpStatusCode.BadRequest.value, "Empty access token")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftProfileStep,
            emptyToken
        )
    }

    @Test
    fun `should handle expired Minecraft access token`() {
        // Arrange
        val expiredToken = "expired-minecraft-token"

        MinecraftApiConfig.useFakeProvider { _, _ ->
            Result.failure(
                AppFailure.ApiError.HttpError(HttpStatusCode.Unauthorized.value, "Token expired")
            )
        }

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMinecraftProfileStep,
            expiredToken
        )
    }
}
