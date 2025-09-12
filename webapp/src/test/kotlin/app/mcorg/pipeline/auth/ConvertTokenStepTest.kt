package app.mcorg.pipeline.auth

import app.mcorg.pipeline.failure.ConvertTokenStepFailure
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.security.JwtHelper
import app.mcorg.presentation.security.getKeys
import app.mcorg.test.utils.TestUtils
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for ConvertTokenStep covering both JWT validation and user data extraction.
 * Tests the complete token-to-TokenProfile conversion pipeline including edge cases and error scenarios.
 */
class ConvertTokenStepTest {

    private lateinit var convertTokenStep: ConvertTokenStep

    @BeforeEach
    fun setup() {
        convertTokenStep = ConvertTokenStep(ISSUER)
    }

    companion object {
        private const val TEST_USER_ID = 123
        private const val TEST_UUID = "550e8400-e29b-41d4-a716-446655440000"
        private const val TEST_USERNAME = "testuser"
        private const val TEST_DISPLAY_NAME = "Test User"
        private val TEST_ROLES = listOf("user", "member")

        private fun createValidJwtToken(): String {
            val (publicKey, privateKey) = JwtHelper.getKeys()
            val algorithm = Algorithm.RSA256(publicKey, privateKey)

            return JWT.create()
                .withIssuer(ISSUER)
                .withAudience(JwtHelper.AUDIENCE)
                .withSubject(TEST_USER_ID.toString())
                .withClaim("sub", TEST_USER_ID)
                .withClaim("minecraft_username", TEST_USERNAME)
                .withClaim("minecraft_uuid", TEST_UUID)
                .withClaim("display_name", TEST_DISPLAY_NAME)
                .withClaim("roles", TEST_ROLES)
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600))) // 1 hour from now
                .withIssuedAt(Date.from(Instant.now()))
                .sign(algorithm)
        }

        private fun createTokenWithCustomClaims(
            userId: Any? = TEST_USER_ID,
            uuid: String? = TEST_UUID,
            username: String? = TEST_USERNAME,
            displayName: String? = TEST_DISPLAY_NAME,
            roles: List<String>? = TEST_ROLES,
            issuer: String = ISSUER,
            audience: String = JwtHelper.AUDIENCE,
            expiration: Instant = Instant.now().plusSeconds(3600)
        ): String {
            val (publicKey, privateKey) = JwtHelper.getKeys()
            val algorithm = Algorithm.RSA256(publicKey, privateKey)

            val builder = JWT.create()
                .withIssuer(issuer)
                .withAudience(audience)
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(expiration))

            // Add subject if provided
            userId?.let {
                builder.withSubject(it.toString())
                builder.withClaim("sub", it as? Int ?: (it as String).toInt())
            }

            // Add optional claims
            username?.let { builder.withClaim("minecraft_username", it) }
            uuid?.let { builder.withClaim("minecraft_uuid", it) }
            displayName?.let { builder.withClaim("display_name", it) }
            roles?.let { builder.withClaim("roles", it) }

            return builder.sign(algorithm)
        }
    }

    // =====================================
    // JWT Validation Tests
    // =====================================

    @Test
    fun `should successfully validate and extract from valid JWT token`() {
        // Arrange
        val validToken = createValidJwtToken()

        // Act & Assert
        val tokenProfile = TestUtils.executeAndAssertSuccess(convertTokenStep, validToken)

        // Verify TokenProfile extraction
        assertEquals(TEST_USER_ID, tokenProfile.id)
        assertEquals(TEST_UUID, tokenProfile.uuid)
        assertEquals(TEST_USERNAME, tokenProfile.minecraftUsername)
        assertEquals(TEST_DISPLAY_NAME, tokenProfile.displayName)
        assertEquals(TEST_ROLES, tokenProfile.roles)
    }

    @Test
    fun `should fail with InvalidToken for empty token`() {
        // Arrange
        val emptyToken = ""

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            convertTokenStep,
            emptyToken,
            ConvertTokenStepFailure.InvalidToken::class.java
        )
        assertEquals(ConvertTokenStepFailure.InvalidToken, error)
    }

    @Test
    fun `should fail with InvalidToken for blank token`() {
        // Arrange
        val blankToken = "   "

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            convertTokenStep,
            blankToken,
            ConvertTokenStepFailure.InvalidToken::class.java
        )
    }

    @Test
    fun `should fail with InvalidToken for malformed token`() {
        // Arrange
        val malformedToken = "not.a.valid.jwt.token"

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            convertTokenStep,
            malformedToken,
            ConvertTokenStepFailure.ConversionError::class.java
        )
    }

    @Test
    fun `should fail with ExpiredToken for expired token`() {
        // Arrange
        val expiredToken = createTokenWithCustomClaims(
            expiration = Instant.now().minusSeconds(3600) // 1 hour ago
        )

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            convertTokenStep,
            expiredToken,
            ConvertTokenStepFailure.ExpiredToken::class.java
        )
    }

    @Test
    fun `should fail with IncorrectClaim for token with wrong issuer`() {
        // Arrange
        val tokenWithWrongIssuer = createTokenWithCustomClaims(issuer = "wrong-issuer")

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            convertTokenStep,
            tokenWithWrongIssuer,
            ConvertTokenStepFailure.IncorrectClaim::class.java
        )

        assertTrue(error is ConvertTokenStepFailure.IncorrectClaim)
        assertEquals("iss", error.claimName)
        assertEquals("wrong-issuer", error.claimValue)
    }

    @Test
    fun `should fail with IncorrectClaim for token with wrong audience`() {
        // Arrange
        val tokenWithWrongAudience = createTokenWithCustomClaims(audience = "wrong-audience")

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            convertTokenStep,
            tokenWithWrongAudience,
            ConvertTokenStepFailure.IncorrectClaim::class.java
        )

        assertTrue(error is ConvertTokenStepFailure.IncorrectClaim)
        assertEquals("aud", error.claimName)
    }

    @Test
    fun `should fail with InvalidToken for token with invalid signature`() {
        // Arrange - Create a token and then modify it to corrupt the signature
        val validToken = createValidJwtToken()
        val corruptedToken = validToken.substring(0, validToken.length - 5) + "XXXXX"

        // Act & Assert
        TestUtils.executeAndAssertFailure(
            convertTokenStep,
            corruptedToken,
            ConvertTokenStepFailure.InvalidToken::class.java
        )
    }

    // =====================================
    // User Data Extraction Tests
    // =====================================

    @Test
    fun `should fail with MissingClaim for token missing user ID`() {
        // Arrange
        val tokenWithoutUserId = createTokenWithCustomClaims(userId = null)

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            convertTokenStep,
            tokenWithoutUserId,
            ConvertTokenStepFailure.MissingClaim::class.java
        )

        assertTrue(error is ConvertTokenStepFailure.MissingClaim)
        assertEquals("sub", error.claimName)
    }

    @Test
    fun `should fail with MissingClaim for token missing minecraft username`() {
        // Arrange
        val tokenWithoutUsername = createTokenWithCustomClaims(username = null)

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            convertTokenStep,
            tokenWithoutUsername,
            ConvertTokenStepFailure.MissingClaim::class.java
        )

        assertTrue(error is ConvertTokenStepFailure.MissingClaim)
        assertEquals("minecraft_username", error.claimName)
    }

    @Test
    fun `should fail with MissingClaim for token missing minecraft UUID`() {
        // Arrange
        val tokenWithoutUuid = createTokenWithCustomClaims(uuid = null)

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            convertTokenStep,
            tokenWithoutUuid,
            ConvertTokenStepFailure.MissingClaim::class.java
        )

        assertTrue(error is ConvertTokenStepFailure.MissingClaim)
        assertEquals("minecraft_uuid", error.claimName)
    }

    @Test
    fun `should fail with MissingClaim for token missing display name`() {
        // Arrange
        val tokenWithoutDisplayName = createTokenWithCustomClaims(displayName = null)

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            convertTokenStep,
            tokenWithoutDisplayName,
            ConvertTokenStepFailure.MissingClaim::class.java
        )

        assertTrue(error is ConvertTokenStepFailure.MissingClaim)
        assertEquals("display_name", error.claimName)
    }

    @Test
    fun `should fail with MissingClaim for token missing roles`() {
        // Arrange
        val tokenWithoutRoles = createTokenWithCustomClaims(roles = null)

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            convertTokenStep,
            tokenWithoutRoles,
            ConvertTokenStepFailure.MissingClaim::class.java
        )

        assertTrue(error is ConvertTokenStepFailure.MissingClaim)
        assertEquals("roles", error.claimName)
    }

    // =====================================
    // Edge Cases and Data Extraction Tests
    // =====================================

    @Test
    fun `should handle empty roles list correctly`() {
        // Arrange
        val tokenWithEmptyRoles = createTokenWithCustomClaims(roles = emptyList())

        // Act & Assert
        val tokenProfile = TestUtils.executeAndAssertSuccess(convertTokenStep, tokenWithEmptyRoles)
        assertEquals(emptyList(), tokenProfile.roles)
    }

    @Test
    fun `should handle single role correctly`() {
        // Arrange
        val singleRole = listOf("admin")
        val tokenWithSingleRole = createTokenWithCustomClaims(roles = singleRole)

        // Act & Assert
        val tokenProfile = TestUtils.executeAndAssertSuccess(convertTokenStep, tokenWithSingleRole)
        assertEquals(singleRole, tokenProfile.roles)
    }

    @Test
    fun `should handle multiple roles correctly`() {
        // Arrange
        val multipleRoles = listOf("user", "member", "moderator", "admin")
        val tokenWithMultipleRoles = createTokenWithCustomClaims(roles = multipleRoles)

        // Act & Assert
        val tokenProfile = TestUtils.executeAndAssertSuccess(convertTokenStep, tokenWithMultipleRoles)
        assertEquals(multipleRoles, tokenProfile.roles)
    }

    @Test
    fun `should handle special characters in display name`() {
        // Arrange
        val specialDisplayName = "Test User 123 @#$%^&*()"
        val tokenWithSpecialName = createTokenWithCustomClaims(displayName = specialDisplayName)

        // Act & Assert
        val tokenProfile = TestUtils.executeAndAssertSuccess(convertTokenStep, tokenWithSpecialName)
        assertEquals(specialDisplayName, tokenProfile.displayName)
    }

    @Test
    fun `should handle unicode characters in display name`() {
        // Arrange
        val unicodeDisplayName = "Test 用户 🎮 Minecraft"
        val tokenWithUnicodeName = createTokenWithCustomClaims(userId = TEST_USER_ID, displayName = unicodeDisplayName)

        // Act & Assert
        val tokenProfile = TestUtils.executeAndAssertSuccess(convertTokenStep, tokenWithUnicodeName)
        assertEquals(unicodeDisplayName, tokenProfile.displayName)
    }

    @Test
    fun `should handle different UUID formats correctly`() {
        // Arrange - Test with UUID without dashes (common Minecraft format)
        val uuidWithoutDashes = "550e8400e29b41d4a716446655440000"
        val tokenWithDifferentUuid = createTokenWithCustomClaims(uuid = uuidWithoutDashes)

        // Act & Assert
        val tokenProfile = TestUtils.executeAndAssertSuccess(convertTokenStep, tokenWithDifferentUuid)
        assertEquals(uuidWithoutDashes, tokenProfile.uuid)
    }

    @Test
    fun `should handle large user ID correctly`() {
        // Arrange
        val largeUserId = Int.MAX_VALUE
        val tokenWithLargeId = createTokenWithCustomClaims(userId = largeUserId)

        // Act & Assert
        val tokenProfile = TestUtils.executeAndAssertSuccess(convertTokenStep, tokenWithLargeId)
        assertEquals(largeUserId, tokenProfile.id)
    }

    @Test
    fun `should handle minimum user ID correctly`() {
        // Arrange
        val minUserId = 1
        val tokenWithMinId = createTokenWithCustomClaims(userId = minUserId)

        // Act & Assert
        val tokenProfile = TestUtils.executeAndAssertSuccess(convertTokenStep, tokenWithMinId)
        assertEquals(minUserId, tokenProfile.id)
    }

    @Test
    fun `should validate TokenProfile role-based properties`() {
        // Arrange - Test with admin and moderator roles
        val adminRoles = listOf("superadmin", "moderator")
        val tokenWithAdminRoles = createTokenWithCustomClaims(roles = adminRoles)

        // Act & Assert
        val tokenProfile = TestUtils.executeAndAssertSuccess(convertTokenStep, tokenWithAdminRoles)
        assertTrue(tokenProfile.isSuperAdmin, "User should be identified as super admin")
        assertTrue(tokenProfile.isModerator, "User should be identified as moderator")
    }

    @Test
    fun `should validate TokenProfile with basic user roles`() {
        // Arrange - Test with only basic user roles
        val userRoles = listOf("user", "member")
        val tokenWithUserRoles = createTokenWithCustomClaims(roles = userRoles)

        // Act & Assert
        val tokenProfile = TestUtils.executeAndAssertSuccess(convertTokenStep, tokenWithUserRoles)
        assertTrue(!tokenProfile.isSuperAdmin, "User should not be identified as super admin")
        assertTrue(!tokenProfile.isModerator, "User should not be identified as moderator")
    }
}
