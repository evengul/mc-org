package app.mcorg.pipeline.auth

import app.mcorg.config.Database
import app.mcorg.config.DatabaseConnectionProvider
import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.auth.commonsteps.ConvertTokenStep
import app.mcorg.pipeline.auth.commonsteps.CreateTokenStep
import app.mcorg.pipeline.auth.commonsteps.CreateUserIfNotExistsStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.consts.ISSUER
import app.mcorg.presentation.security.JwtHelper
import app.mcorg.presentation.security.getKeys
import app.mcorg.test.utils.TestUtils
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for the MC-ORG Authentication Pipeline.
 *
 * Tests the complete authentication flow including:
 * - JWT token validation and extraction
 * - User creation from OAuth response
 * - Authentication failure scenarios
 * - Token expiration handling
 * - Local and test authentication modes
 *
 * Priority: High (Foundation for all authenticated functionality)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticationPipelineTest {

    // Mock database components
    private val mockConnection = mockk<Connection>()
    private val mockProvider = mockk<DatabaseConnectionProvider>()
    private val mockStatement = mockk<PreparedStatement>()
    private val mockResultSet = mockk<ResultSet>()
    val updateUsernameStatement = mockk<PreparedStatement>()

    // Test data
    private val testMinecraftProfile = MinecraftProfile(
        uuid = "test-uuid-123",
        username = "TestPlayer"
    )

    private val testTokenProfile = TokenProfile(
        id = 1,
        uuid = "test-uuid-123",
        minecraftUsername = "TestPlayer",
        displayName = "TestPlayer",
        roles = emptyList()
    )

    @BeforeEach
    fun setup() {
        // Setup mock database
        Database.setProvider(mockProvider)
        every { mockProvider.getConnection() } returns mockConnection
        every { mockConnection.close() } just Runs
        every { mockConnection.autoCommit = any() } just Runs
        every { mockConnection.commit() } just Runs
        every { mockConnection.rollback() } just Runs

        // Setup common statement behaviors
        every { mockStatement.close() } just Runs
        every { mockResultSet.close() } just Runs
        every { mockStatement.setString(any(), any()) } just Runs
        every { mockStatement.setInt(any(), any()) } just Runs
    }

    @AfterEach
    fun teardown() {
        Database.resetProvider()
        unmockkAll()
    }

    // ===============================
    // JWT Token Validation Tests
    // ===============================

    @Test
    fun `ConvertTokenStep should successfully validate valid JWT token`() {
        // Arrange
        val validToken = createValidTestToken(testTokenProfile)
        val convertStep = ConvertTokenStep(ISSUER)

        // Act
        val tokenProfile = TestUtils.executeAndAssertSuccess(convertStep, validToken)

        // Assert
        assertEquals(testTokenProfile.id, tokenProfile.id)
        assertEquals(testTokenProfile.uuid, tokenProfile.uuid)
        assertEquals(testTokenProfile.minecraftUsername, tokenProfile.minecraftUsername)
        assertEquals(testTokenProfile.displayName, tokenProfile.displayName)
        assertEquals(testTokenProfile.roles, tokenProfile.roles)
    }

    @Test
    fun `ConvertTokenStep should fail with invalid signature`() {
        // Arrange
        val invalidToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature"
        val convertStep = ConvertTokenStep(ISSUER)

        // Act
        TestUtils.executeAndAssertFailure(convertStep, invalidToken)
    }

    @Test
    fun `ConvertTokenStep should fail with expired token`() {
        // Arrange
        val expiredToken = createExpiredTestToken(testTokenProfile)
        val convertStep = ConvertTokenStep(ISSUER)

        // Act
        TestUtils.executeAndAssertFailure(
            convertStep,
            expiredToken
        )
    }

    @Test
    fun `ConvertTokenStep should fail with wrong issuer`() {
        // Arrange
        val tokenWithWrongIssuer = createTestTokenWithIssuer(testTokenProfile, "wrong-issuer")
        val convertStep = ConvertTokenStep(ISSUER)

        // Act
        TestUtils.executeAndAssertFailure(
            convertStep,
            tokenWithWrongIssuer
        )
    }

    @Test
    fun `ConvertTokenStep should fail with missing required claims`() {
        // Arrange
        val tokenWithMissingClaims = createTestTokenWithMissingClaims()
        val convertStep = ConvertTokenStep(ISSUER)

        // Act
        TestUtils.executeAndAssertFailure(
            convertStep,
            tokenWithMissingClaims
        )
    }

    // ===============================
    // JWT Token Creation Tests
    // ===============================

    @Test
    fun `CreateTokenStep should successfully create valid JWT token`() {
        // Arrange
        val createTokenStep = CreateTokenStep

        // Act
        val token = TestUtils.executeAndAssertSuccess(createTokenStep, testTokenProfile)

        // Assert
        assertNotNull(token, "Created token should not be null")
        assertTrue(token.isNotBlank(), "Created token should not be blank")

        // Verify the created token can be validated
        TestUtils.executeAndAssertSuccess(ConvertTokenStep(ISSUER), token)
    }

    @Test
    fun `CreateTokenStep should include all required claims in token`() {
        // Arrange
        val profileWithRoles = testTokenProfile.copy(roles = listOf("ADMIN", "MEMBER"))

        // Act
        val token = TestUtils.executeAndAssertSuccess(CreateTokenStep, profileWithRoles)

        // Assert

        // Decode and verify claims (without signature verification for testing)
        val jwt = JWT.decode(token)
        assertEquals(profileWithRoles.id, jwt.getClaim("sub").asInt())
        assertEquals(profileWithRoles.minecraftUsername, jwt.getClaim("minecraft_username").asString())
        assertEquals(profileWithRoles.uuid, jwt.getClaim("minecraft_uuid").asString())
        assertEquals(profileWithRoles.displayName, jwt.getClaim("display_name").asString())
        assertEquals(profileWithRoles.roles, jwt.getClaim("roles").asList(String::class.java))
        assertEquals(ISSUER, jwt.issuer)
        assertEquals(JwtHelper.AUDIENCE, jwt.audience[0])
    }

    // ===============================
    // User Creation Pipeline Tests
    // ===============================

    @Test
    fun `CreateUserIfNotExistsStep should return existing user when UUID exists`() {
        // Arrange
        setupMockForExistingUser()

        // Act
        val tokenProfile = TestUtils.executeAndAssertSuccess(CreateUserIfNotExistsStep, testMinecraftProfile)

        // Assert
        assertEquals(testTokenProfile.id, tokenProfile.id)
        assertEquals(testTokenProfile.uuid, tokenProfile.uuid)
        assertEquals(testTokenProfile.minecraftUsername, tokenProfile.minecraftUsername)
    }

    @Test
    fun `CreateUserIfNotExistsStep should create new user when UUID does not exist`() {
        // Arrange
        setupMockForNewUser()

        // Act
        val tokenProfile = TestUtils.executeAndAssertSuccess(CreateUserIfNotExistsStep, testMinecraftProfile)

        // Assert
        assertEquals(testMinecraftProfile.uuid, tokenProfile.uuid)
        assertEquals(testMinecraftProfile.username, tokenProfile.minecraftUsername)
        assertEquals(testMinecraftProfile.username, tokenProfile.displayName)
    }

    @Test
    fun `CreateUserIfNotExistsStep should update username when UUID exists but username differs`() {
        // Arrange
        val oldUsername = "OldPlayerName"
        val newProfile = testMinecraftProfile.copy(username = "NewPlayerName")
        setupMockForUsernameUpdate(oldUsername)

        // Act
        val tokenProfile = TestUtils.executeAndAssertSuccess(CreateUserIfNotExistsStep, newProfile)

        // Assert
        assertEquals(newProfile.username, tokenProfile.minecraftUsername)
        assertEquals(newProfile.username, tokenProfile.displayName)

        // Verify update operations were called
        verify { updateUsernameStatement.setString(1, newProfile.username) }
        verify { updateUsernameStatement.executeUpdate() }
    }

    @Test
    fun `CreateUserIfNotExistsStep should handle database errors gracefully`() {
        // Arrange
        every { mockConnection.prepareStatement(any()) } throws SQLException("Database connection failed")

        // Act
        TestUtils.executeAndAssertFailure(
            CreateUserIfNotExistsStep,
            testMinecraftProfile
        )
    }

    // ===============================
    // Complete Authentication Flow Tests
    // ===============================

    @Test
    fun `complete OAuth authentication flow should succeed with valid Minecraft profile`() {
        // Arrange
        setupMockForNewUser()

        // Act - Simulate complete flow: MinecraftProfile -> User -> JWT Token -> Validation
        val tokenProfile = TestUtils.executeAndAssertSuccess(CreateUserIfNotExistsStep, testMinecraftProfile)
        val token = TestUtils.executeAndAssertSuccess(CreateTokenStep, tokenProfile)
        val convertedProfile = TestUtils.executeAndAssertSuccess(ConvertTokenStep(ISSUER), token)

        // Assert - Complete round-trip maintains data integrity
        assertEquals(testMinecraftProfile.uuid, convertedProfile.uuid)
        assertEquals(testMinecraftProfile.username, convertedProfile.minecraftUsername)
        assertEquals(testMinecraftProfile.username, convertedProfile.displayName)
    }

    @Test
    fun `authentication flow should handle user update scenario`() {
        // Arrange
        val oldUsername = "OldPlayer"
        val updatedProfile = testMinecraftProfile.copy(username = "UpdatedPlayer")
        setupMockForUsernameUpdate(oldUsername)

        // Act - Simulate user with updated username
        val tokenProfile = TestUtils.executeAndAssertSuccess(CreateUserIfNotExistsStep, updatedProfile)
        val token = TestUtils.executeAndAssertSuccess(CreateTokenStep, tokenProfile)
        val finalProfile = TestUtils.executeAndAssertSuccess(ConvertTokenStep(ISSUER), token)

        // Assert - Updated data is preserved through complete flow
        assertEquals(updatedProfile.username, finalProfile.minecraftUsername)
        assertEquals(updatedProfile.username, finalProfile.displayName)
    }

    // ===============================
    // Edge Cases and Error Scenarios
    // ===============================

    @Test
    fun `authentication should fail gracefully with null or empty token`() {
        // Arrange
        val convertStep = ConvertTokenStep(ISSUER)

        // Act & Assert - Empty token
        TestUtils.executeAndAssertFailure(
            convertStep,
            ""
        )

        // Act & Assert - Blank token
        TestUtils.executeAndAssertFailure(
            convertStep,
            "   "
        )
    }

    @Test
    fun `authentication should handle malformed JWT tokens`() = runBlocking {
        // Arrange
        val convertStep = ConvertTokenStep(ISSUER)
        val malformedTokens = listOf(
            "not.a.jwt",
            "missing.parts",
            "too.many.parts.here.invalid",
            "header.payload", // Missing signature
            "invalid-base64-encoding"
        )

        // Act & Assert
        malformedTokens.forEach { malformedToken ->
            val result = convertStep.process(malformedToken)
            TestUtils.assertResultFailure(
                result,
                AppFailure.AuthError.ConvertTokenError::class.java
            )
        }
    }

    // ===============================
    // Helper Methods
    // ===============================

    private fun createValidTestToken(profile: TokenProfile): String {
        val (publicKey, privateKey) = JwtHelper.getKeys()
        return JWT.create()
            .withAudience(JwtHelper.AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("sub", profile.id)
            .withClaim("minecraft_username", profile.minecraftUsername)
            .withClaim("minecraft_uuid", profile.uuid)
            .withClaim("display_name", profile.displayName)
            .withClaim("roles", profile.roles)
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000)) // 1 hour from now
            .sign(Algorithm.RSA256(publicKey, privateKey))
    }

    private fun createExpiredTestToken(profile: TokenProfile): String {
        val (publicKey, privateKey) = JwtHelper.getKeys()
        return JWT.create()
            .withAudience(JwtHelper.AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("sub", profile.id)
            .withClaim("minecraft_username", profile.minecraftUsername)
            .withClaim("minecraft_uuid", profile.uuid)
            .withClaim("display_name", profile.displayName)
            .withClaim("roles", profile.roles)
            .withExpiresAt(Date(System.currentTimeMillis() - 3600000)) // 1 hour ago (expired)
            .sign(Algorithm.RSA256(publicKey, privateKey))
    }

    private fun createTestTokenWithIssuer(profile: TokenProfile, issuer: String): String {
        val (publicKey, privateKey) = JwtHelper.getKeys()
        return JWT.create()
            .withAudience(JwtHelper.AUDIENCE)
            .withIssuer(issuer) // Wrong issuer
            .withClaim("sub", profile.id)
            .withClaim("minecraft_username", profile.minecraftUsername)
            .withClaim("minecraft_uuid", profile.uuid)
            .withClaim("display_name", profile.displayName)
            .withClaim("roles", profile.roles)
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
            .sign(Algorithm.RSA256(publicKey, privateKey))
    }

    private fun createTestTokenWithMissingClaims(): String {
        val (publicKey, privateKey) = JwtHelper.getKeys()
        return JWT.create()
            .withAudience(JwtHelper.AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("sub", testTokenProfile.id)
            // Missing required claims: minecraft_username, minecraft_uuid, display_name, roles
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
            .sign(Algorithm.RSA256(publicKey, privateKey))
    }

    private fun setupMockForExistingUser() {
        every { mockConnection.prepareStatement(match { it.contains("SELECT u.id, u.email, mp.uuid, mp.username") }) } returns mockStatement
        every { mockStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns true
        every { mockResultSet.getInt("id") } returns testTokenProfile.id
        every { mockResultSet.getString("email") } returns "test@example.com"
        every { mockResultSet.getString("uuid") } returns testTokenProfile.uuid
        every { mockResultSet.getString("username") } returns testTokenProfile.minecraftUsername

        val globalRolesStatement = mockk<PreparedStatement>()
        val globalRolesResultSet = mockk<ResultSet>()

        every { mockConnection.prepareStatement(match { it.contains("FROM global_user_roles") }) } returns globalRolesStatement
        every { globalRolesStatement.setInt(any(), any()) } just Runs
        every { globalRolesStatement.executeQuery() } returns globalRolesResultSet
        every { globalRolesResultSet.next() } returns false
        every { globalRolesResultSet.close() } just Runs
        every { globalRolesStatement.close() } just Runs
    }

    private fun setupMockForNewUser() {
        // Mock check user query - no existing user
        every { mockConnection.prepareStatement(match { it.contains("SELECT u.id, u.email, mp.uuid, mp.username") }) } returns mockStatement
        every { mockStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns false

        // Mock user creation
        val createUserStatement = mockk<PreparedStatement>()
        val createProfileStatement = mockk<PreparedStatement>()
        val userResultSet = mockk<ResultSet>()

        every { mockConnection.prepareStatement(match { it.contains("INSERT INTO users") }) } returns createUserStatement
        every { mockConnection.prepareStatement(match { it.contains("INSERT INTO minecraft_profiles") }) } returns createProfileStatement

        every { createUserStatement.setString(any(), any()) } just Runs
        every { createUserStatement.executeUpdate() } returns 1
        every { createUserStatement.executeQuery() } returns userResultSet
        every { createUserStatement.close() } just Runs

        every { createProfileStatement.setInt(any(), any()) } just Runs
        every { createProfileStatement.setString(any(), any()) } just Runs
        every { createProfileStatement.executeUpdate() } returns 1
        every { createProfileStatement.close() } just Runs

        every { userResultSet.next() } returns true
        every { userResultSet.getInt("id") } returns testTokenProfile.id
        every { userResultSet.close() } just Runs
    }

    private fun setupMockForUsernameUpdate(oldUsername: String) {
        // Mock check user query - existing user with old username
        every { mockConnection.prepareStatement(match { it.contains("SELECT u.id, u.email, mp.uuid, mp.username") }) } returns mockStatement
        every { mockStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns true
        every { mockResultSet.getInt("id") } returns testTokenProfile.id
        every { mockResultSet.getString("email") } returns "test@example.com"
        every { mockResultSet.getString("uuid") } returns testTokenProfile.uuid
        every { mockResultSet.getString("username") } returns oldUsername

        // Mock username update operations
        val globalRolesStatement = mockk<PreparedStatement>()
        val globalRolesResultSet = mockk<ResultSet>()
        val updateWorldMembersStatement = mockk<PreparedStatement>()

        every { mockConnection.prepareStatement(match { it.contains("FROM global_user_roles") }) } returns globalRolesStatement
        every { mockConnection.prepareStatement(match { it.contains("UPDATE minecraft_profiles") }) } returns updateUsernameStatement
        every { mockConnection.prepareStatement(match { it.contains("UPDATE world_members") }) } returns updateWorldMembersStatement

        every { globalRolesStatement.setInt(any(), any()) } just Runs
        every { globalRolesStatement.executeQuery() } returns globalRolesResultSet
        every { globalRolesResultSet.next() } returns false
        every { globalRolesResultSet.close() } just Runs
        every { globalRolesStatement.close() } just Runs

        every { updateUsernameStatement.setString(any(), any()) } just Runs
        every { updateUsernameStatement.executeUpdate() } returns 1
        every { updateUsernameStatement.close() } just Runs

        every { updateWorldMembersStatement.setString(any(), any()) } just Runs
        every { updateWorldMembersStatement.setInt(any(), any()) } just Runs
        every { updateWorldMembersStatement.executeUpdate() } returns 1
        every { updateWorldMembersStatement.close() } just Runs
    }
}
