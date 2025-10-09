package app.mcorg.pipeline.auth

import app.mcorg.pipeline.failure.GetMicrosoftCodeFailure
import app.mcorg.test.utils.TestUtils
import io.ktor.http.Parameters
import io.ktor.http.parametersOf
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test suite for GetMicrosoftCodeStep - OAuth code parameter extraction
 *
 * Tests OAuth redirect parameter handling:
 * - Successful code extraction from URL parameters
 * - Error parameter handling (user denial, invalid request, etc.)
 * - Missing parameter scenarios
 *
 * Priority: High (Critical for OAuth flow initiation)
 */
class GetMicrosoftCodeStepTest {

    @Test
    fun `should successfully extract code from parameters`() {
        // Arrange
        val parameters = parametersOf("code", "test_oauth_code_123")

        // Act
        val result = TestUtils.executeAndAssertSuccess(GetMicrosoftCodeStep, parameters)

        // Assert
        assertEquals("test_oauth_code_123", result)
    }

    @Test
    fun `should extract code when multiple parameters present`() {
        // Arrange
        val parameters = parametersOf(
            "code" to listOf("oauth_code_456"),
            "state" to listOf("random_state"),
            "session_state" to listOf("session_123")
        )

        // Act
        val result = TestUtils.executeAndAssertSuccess(GetMicrosoftCodeStep, parameters)

        // Assert
        assertEquals("oauth_code_456", result)
    }

    @Test
    fun `should fail with Error when error parameter is present`() {
        // Arrange
        val parameters = parametersOf(
            "error" to listOf("access_denied"),
            "description" to listOf("The user denied the request")
        )

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            GetMicrosoftCodeStep,
            parameters,
            GetMicrosoftCodeFailure.Error::class.java
        )
        when(error) {
            is GetMicrosoftCodeFailure.Error -> {
                assertEquals("access_denied", error.error)
                assertEquals("The user denied the request", error.description)
            }
            else -> fail("Unexpected error type: ${error.javaClass.name}")
        }
    }

    @Test
    fun `should fail with Error when error parameter present without description`() {
        // Arrange
        val parameters = parametersOf("error", "invalid_request")

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            GetMicrosoftCodeStep,
            parameters,
            GetMicrosoftCodeFailure.Error::class.java
        )
        when(error) {
            is GetMicrosoftCodeFailure.Error -> {
                assertEquals("invalid_request", error.error)
                assertEquals("Some error occurred", error.description)
            }
            else -> fail("Unexpected error type: ${error.javaClass.name}")
        }
    }

    @Test
    fun `should fail with MissingCode when no code or error parameter present`() {
        // Arrange
        val parameters = parametersOf(
            "state" to listOf("random_state"),
            "session_state" to listOf("session_123")
        )

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMicrosoftCodeStep,
            parameters,
            GetMicrosoftCodeFailure.MissingCode::class.java
        )
    }

    @Test
    fun `should fail with MissingCode when parameters are empty`() {
        // Arrange
        val parameters = Parameters.Empty

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            GetMicrosoftCodeStep,
            parameters,
            GetMicrosoftCodeFailure.MissingCode::class.java
        )
    }

    @Test
    fun `should handle common OAuth error scenarios`() {
        // Test various OAuth error scenarios
        val errorScenarios = listOf(
            "access_denied" to "User denied access",
            "invalid_request" to "Invalid OAuth request",
            "unauthorized_client" to "Client not authorized",
            "unsupported_response_type" to "Response type not supported",
            "invalid_scope" to "Invalid scope requested",
            "server_error" to "OAuth server error",
            "temporarily_unavailable" to "Service temporarily unavailable"
        )

        errorScenarios.forEach { (errorCode, description) ->
            // Arrange
            val parameters = parametersOf(
                "error" to listOf(errorCode),
                "description" to listOf(description)
            )

            // Act & Assert
            val error = TestUtils.executeAndAssertFailure(
                GetMicrosoftCodeStep,
                parameters,
                GetMicrosoftCodeFailure.Error::class.java
            )
            when(error) {
                is GetMicrosoftCodeFailure.Error -> {
                    assertEquals(errorCode, error.error)
                    assertEquals(description, error.description)
                }
                else -> fail("Unexpected error type: ${error.javaClass.name}")
            }
        }
    }
}
