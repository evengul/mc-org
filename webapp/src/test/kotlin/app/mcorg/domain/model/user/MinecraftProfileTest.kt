package app.mcorg.domain.model.user

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for MinecraftProfile domain model.
 *
 * Tests Minecraft user data structure including:
 * - UUID and username validation patterns
 * - Data class properties and construction
 * - Edge cases for Minecraft-specific constraints
 * - Property access patterns used in authentication flows
 *
 * Priority: High (Essential for Minecraft OAuth integration)
 */
class MinecraftProfileTest {

    // Test data constants
    companion object {
        private const val VALID_UUID = "550e8400-e29b-41d4-a716-446655440000"
        private const val VALID_USERNAME = "TestPlayer"
        private const val VALID_USERNAME_UNDERSCORE = "Test_Player_123"
        private const val VALID_USERNAME_MIN_LENGTH = "ab"
        private const val VALID_USERNAME_MAX_LENGTH = "abcdefghijklmnop" // 15 chars
    }

    // ===============================
    // Constructor and Property Tests
    // ===============================

    @Test
    fun `should create MinecraftProfile with valid UUID and username`() {
        // Act
        val profile = MinecraftProfile(
            uuid = VALID_UUID,
            username = VALID_USERNAME
        )

        // Assert
        assertEquals(VALID_UUID, profile.uuid)
        assertEquals(VALID_USERNAME, profile.username)
    }

    @Test
    fun `should create MinecraftProfile with username containing underscores and numbers`() {
        // Act
        val profile = MinecraftProfile(
            uuid = VALID_UUID,
            username = VALID_USERNAME_UNDERSCORE
        )

        // Assert
        assertEquals(VALID_UUID, profile.uuid)
        assertEquals(VALID_USERNAME_UNDERSCORE, profile.username)
    }

    @Test
    fun `should handle minimum length username`() {
        // Act
        val profile = MinecraftProfile(
            uuid = VALID_UUID,
            username = VALID_USERNAME_MIN_LENGTH
        )

        // Assert
        assertEquals(VALID_USERNAME_MIN_LENGTH, profile.username)
    }

    @Test
    fun `should handle maximum length username`() {
        // Act
        val profile = MinecraftProfile(
            uuid = VALID_UUID,
            username = VALID_USERNAME_MAX_LENGTH
        )

        // Assert
        assertEquals(VALID_USERNAME_MAX_LENGTH, profile.username)
    }

    // ===============================
    // UUID Format Tests
    // ===============================

    @Test
    fun `should handle UUID with hyphens`() {
        // Arrange
        val uuidWithHyphens = "550e8400-e29b-41d4-a716-446655440000"

        // Act
        val profile = MinecraftProfile(
            uuid = uuidWithHyphens,
            username = VALID_USERNAME
        )

        // Assert
        assertEquals(uuidWithHyphens, profile.uuid)
    }

    @Test
    fun `should handle UUID without hyphens`() {
        // Arrange
        val uuidWithoutHyphens = "550e8400e29b41d4a716446655440000"

        // Act
        val profile = MinecraftProfile(
            uuid = uuidWithoutHyphens,
            username = VALID_USERNAME
        )

        // Assert
        assertEquals(uuidWithoutHyphens, profile.uuid)
    }

    @Test
    fun `should handle uppercase UUID`() {
        // Arrange
        val uppercaseUuid = "550E8400-E29B-41D4-A716-446655440000"

        // Act
        val profile = MinecraftProfile(
            uuid = uppercaseUuid,
            username = VALID_USERNAME
        )

        // Assert
        assertEquals(uppercaseUuid, profile.uuid)
    }

    @Test
    fun `should handle mixed case UUID`() {
        // Arrange
        val mixedCaseUuid = "550e8400-E29B-41d4-A716-446655440000"

        // Act
        val profile = MinecraftProfile(
            uuid = mixedCaseUuid,
            username = VALID_USERNAME
        )

        // Assert
        assertEquals(mixedCaseUuid, profile.uuid)
    }

    // ===============================
    // Username Edge Cases
    // ===============================

    @Test
    fun `should handle username with mixed case`() {
        // Arrange
        val mixedCaseUsername = "TestPlayerABC"

        // Act
        val profile = MinecraftProfile(
            uuid = VALID_UUID,
            username = mixedCaseUsername
        )

        // Assert
        assertEquals(mixedCaseUsername, profile.username)
    }

    @Test
    fun `should handle username with numbers`() {
        // Arrange
        val usernameWithNumbers = "Player123"

        // Act
        val profile = MinecraftProfile(
            uuid = VALID_UUID,
            username = usernameWithNumbers
        )

        // Assert
        assertEquals(usernameWithNumbers, profile.username)
    }

    @Test
    fun `should handle username starting with number`() {
        // Arrange - Note: Minecraft actually allows this
        val usernameStartingWithNumber = "123Player"

        // Act
        val profile = MinecraftProfile(
            uuid = VALID_UUID,
            username = usernameStartingWithNumber
        )

        // Assert
        assertEquals(usernameStartingWithNumber, profile.username)
    }

    @Test
    fun `should handle username with consecutive underscores`() {
        // Arrange
        val usernameWithConsecutiveUnderscores = "Test__Player"

        // Act
        val profile = MinecraftProfile(
            uuid = VALID_UUID,
            username = usernameWithConsecutiveUnderscores
        )

        // Assert
        assertEquals(usernameWithConsecutiveUnderscores, profile.username)
    }

    // ===============================
    // Data Class Behavior Tests
    // ===============================

    @Test
    fun `should support data class equality correctly`() {
        // Arrange
        val profile1 = MinecraftProfile(
            uuid = VALID_UUID,
            username = VALID_USERNAME
        )

        val profile2 = MinecraftProfile(
            uuid = VALID_UUID,
            username = VALID_USERNAME
        )

        val profile3 = MinecraftProfile(
            uuid = "different-uuid-here",
            username = VALID_USERNAME
        )

        // Assert
        assertEquals(profile1, profile2)
        assertNotEquals(profile1, profile3)
        assertEquals(profile1.hashCode(), profile2.hashCode())
    }

    @Test
    fun `should support data class copy functionality`() {
        // Arrange
        val originalProfile = MinecraftProfile(
            uuid = VALID_UUID,
            username = VALID_USERNAME
        )

        // Act
        val copiedProfile = originalProfile.copy(username = "NewUsername")

        // Assert
        assertEquals(VALID_UUID, copiedProfile.uuid)
        assertEquals("NewUsername", copiedProfile.username)

        // Original should be unchanged
        assertEquals(VALID_USERNAME, originalProfile.username)
    }

    @Test
    fun `should generate proper toString representation`() {
        // Arrange
        val profile = MinecraftProfile(
            uuid = VALID_UUID,
            username = VALID_USERNAME
        )

        // Act
        val stringRepresentation = profile.toString()

        // Assert
        assertTrue(stringRepresentation.contains(VALID_UUID))
        assertTrue(stringRepresentation.contains(VALID_USERNAME))
        assertTrue(stringRepresentation.contains("MinecraftProfile"))
    }

    // ===============================
    // Component Functions Tests
    // ===============================

    @Test
    fun `should support destructuring assignment`() {
        // Arrange
        val profile = MinecraftProfile(
            uuid = VALID_UUID,
            username = VALID_USERNAME
        )

        // Act
        val (uuid, username) = profile

        // Assert
        assertEquals(VALID_UUID, uuid)
        assertEquals(VALID_USERNAME, username)
    }

    // ===============================
    // Authentication Integration Tests
    // ===============================

    @Test
    fun `should work correctly in authentication context`() {
        // Arrange - Simulate data from Microsoft OAuth
        val oauthUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        val oauthUsername = "MinecraftPlayer"

        // Act
        val profile = MinecraftProfile(
            uuid = oauthUuid,
            username = oauthUsername
        )

        // Assert - Properties should be directly accessible for TokenProfile creation
        assertEquals(oauthUuid, profile.uuid)
        assertEquals(oauthUsername, profile.username)
    }

    @Test
    fun `should handle special characters in UUID correctly`() {
        // Arrange - Test with various valid UUID formats
        val testCases = listOf(
            "00000000-0000-0000-0000-000000000000", // All zeros
            "ffffffff-ffff-ffff-ffff-ffffffffffff", // All f's
            "12345678-90ab-cdef-1234-567890abcdef"  // Mixed hex
        )

        testCases.forEach { testUuid ->
            // Act
            val profile = MinecraftProfile(
                uuid = testUuid,
                username = VALID_USERNAME
            )

            // Assert
            assertEquals(testUuid, profile.uuid)
        }
    }

    // ===============================
    // Edge Case Validation Tests
    // ===============================

    @Test
    fun `should handle empty string UUID and username`() {
        // Note: This tests the data class construction behavior
        // In real usage, validation would happen at the pipeline level

        // Act
        val profile = MinecraftProfile(
            uuid = "",
            username = ""
        )

        // Assert
        assertEquals("", profile.uuid)
        assertEquals("", profile.username)
    }

    @Test
    fun `should handle whitespace in properties`() {
        // Arrange - Note: Real validation would trim these at pipeline level
        val uuidWithSpaces = " $VALID_UUID "
        val usernameWithSpaces = " $VALID_USERNAME "

        // Act
        val profile = MinecraftProfile(
            uuid = uuidWithSpaces,
            username = usernameWithSpaces
        )

        // Assert
        assertEquals(uuidWithSpaces, profile.uuid)
        assertEquals(usernameWithSpaces, profile.username)
    }
}
