package app.mcorg.pipeline.auth

import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.test.fixtures.TestDataFactory
import app.mcorg.test.utils.TestUtils
import app.mcorg.presentation.security.JwtHelper
import com.auth0.jwt.JWT
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Comprehensive test suite for CreateTokenStep covering JWT token creation.
 * Tests the complete TokenProfile-to-JWT conversion pipeline including validation and error scenarios.
 */
class CreateTokenStepTest {

    @Test
    fun `should successfully create JWT token with valid TokenProfile`() {
        // Arrange
        val tokenProfile = TestDataFactory.createTestTokenProfile(
            id = 123,
            uuid = "550e8400-e29b-41d4-a716-446655440000",
            minecraftUsername = "TestPlayer",
            displayName = "Test User",
            roles = listOf("member")
        )

        // Act
        val result = TestUtils.executeAndAssertSuccess(CreateTokenStep, tokenProfile)

        // Assert
        assertNotNull(result)
        assertTrue(result.isNotBlank(), "JWT token should not be blank")

        // Verify JWT structure
        val decodedJWT = JWT.decode(result)
        assertEquals("mcorg", decodedJWT.issuer)
        assertEquals(JwtHelper.AUDIENCE, decodedJWT.audience.first())
        assertEquals(123, decodedJWT.getClaim("sub").asInt())
        assertEquals("TestPlayer", decodedJWT.getClaim("minecraft_username").asString())
        assertEquals("550e8400-e29b-41d4-a716-446655440000", decodedJWT.getClaim("minecraft_uuid").asString())
        assertEquals("Test User", decodedJWT.getClaim("display_name").asString())
        assertEquals(listOf("member"), decodedJWT.getClaim("roles").asList(String::class.java))
        assertNotNull(decodedJWT.expiresAt)
    }

    @Test
    fun `should create valid JWT token with empty roles list`() {
        // Arrange
        val tokenProfile = TestDataFactory.createTestTokenProfile(
            id = 456,
            roles = emptyList()
        )

        // Act
        val result = TestUtils.executeAndAssertSuccess(CreateTokenStep, tokenProfile)

        // Assert
        val decodedJWT = JWT.decode(result)
        assertEquals(456, decodedJWT.getClaim("sub").asInt())
        assertEquals(emptyList<String>(), decodedJWT.getClaim("roles").asList(String::class.java))
    }

    @Test
    fun `should create valid JWT token with multiple roles`() {
        // Arrange
        val tokenProfile = TestDataFactory.createTestTokenProfile(
            id = 789,
            roles = listOf("admin", "moderator", "premium")
        )
        // Act
        val result = TestUtils.executeAndAssertSuccess(CreateTokenStep, tokenProfile)

        // Assert
        val decodedJWT = JWT.decode(result)
        assertEquals(789, decodedJWT.getClaim("sub").asInt())
        assertEquals(listOf("admin", "moderator", "premium"), decodedJWT.getClaim("roles").asList(String::class.java))
    }

    @Test
    fun `should create JWT token with special characters in display name`() {
        // Arrange
        val tokenProfile = TestDataFactory.createTestTokenProfile(
            id = 101,
            displayName = "Test User @#$%^&*()",
            minecraftUsername = "Player_123"
        )

        // Act
        val result = TestUtils.executeAndAssertSuccess(CreateTokenStep, tokenProfile)

        // Assert
        val decodedJWT = JWT.decode(result)
        assertEquals("Test User @#$%^&*()", decodedJWT.getClaim("display_name").asString())
        assertEquals("Player_123", decodedJWT.getClaim("minecraft_username").asString())
    }

    @Test
    fun `should set expiration time correctly`() {
        // Arrange
        val tokenProfile = TestDataFactory.createTestTokenProfile()
        val beforeCreation = System.currentTimeMillis()

        // Act
        val result = TestUtils.executeAndAssertSuccess(CreateTokenStep, tokenProfile)

        // Assert
        val decodedJWT = JWT.decode(result)
        val expirationTime = decodedJWT.expiresAt.time
        val expectedMinExpiration = beforeCreation + (8 * 60 * 60 * 1000) - 1000 // 8 hours minus 1 second buffer
        val expectedMaxExpiration = System.currentTimeMillis() + (8 * 60 * 60 * 1000) + 1000 // 8 hours plus 1 second buffer

        assertTrue(expirationTime >= expectedMinExpiration, "Token should expire in approximately 8 hours")
        assertTrue(expirationTime <= expectedMaxExpiration, "Token should expire in approximately 8 hours")
    }
}
