package app.mcorg.domain.model.user

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for TokenProfile domain model.
 *
 * Tests authentication token data structure including:
 * - Data class properties and construction
 * - Role-based property calculations (isSuperAdmin, isModerator, etc.)
 * - Edge cases and boundary conditions for role lists
 * - Property access patterns used throughout the application
 *
 * Priority: High (Core authentication data structure)
 */
class TokenProfileTest {

    // Test data constants
    companion object {
        private const val TEST_USER_ID = 42
        private const val TEST_UUID = "550e8400-e29b-41d4-a716-446655440000"
        private const val TEST_MINECRAFT_USERNAME = "TestPlayer"
        private const val TEST_DISPLAY_NAME = "Test Player Display"
    }

    // ===============================
    // Constructor and Property Tests
    // ===============================

    @Test
    fun `should create TokenProfile with all properties`() {
        // Arrange
        val roles = listOf("member", "idea_creator")

        // Act
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = roles
        )

        // Assert
        assertEquals(TEST_USER_ID, tokenProfile.id)
        assertEquals(TEST_UUID, tokenProfile.uuid)
        assertEquals(TEST_MINECRAFT_USERNAME, tokenProfile.minecraftUsername)
        assertEquals(TEST_DISPLAY_NAME, tokenProfile.displayName)
        assertEquals(roles, tokenProfile.roles)
    }

    @Test
    fun `should create TokenProfile with empty roles list`() {
        // Act
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = emptyList()
        )

        // Assert
        assertEquals(emptyList<String>(), tokenProfile.roles)
        assertFalse(tokenProfile.isSuperAdmin)
        assertFalse(tokenProfile.isModerator)
        assertFalse(tokenProfile.isIdeaCreator)
        assertFalse(tokenProfile.isBanned)
    }

    // ===============================
    // Role-Based Property Tests
    // ===============================

    @Test
    fun `isSuperAdmin should return true when superadmin role present`() {
        // Arrange
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("superadmin", "member")
        )

        // Assert
        assertTrue(tokenProfile.isSuperAdmin)
    }

    @Test
    fun `isSuperAdmin should return false when superadmin role not present`() {
        // Arrange
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("member", "moderator")
        )

        // Assert
        assertFalse(tokenProfile.isSuperAdmin)
    }

    @Test
    fun `isModerator should return true when moderator role present`() {
        // Arrange
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("moderator", "member")
        )

        // Assert
        assertTrue(tokenProfile.isModerator)
    }

    @Test
    fun `isModerator should return false when moderator role not present`() {
        // Arrange
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("member", "idea_creator")
        )

        // Assert
        assertFalse(tokenProfile.isModerator)
    }

    @Test
    fun `isIdeaCreator should return true when idea_creator role present`() {
        // Arrange
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("idea_creator", "member")
        )

        // Assert
        assertTrue(tokenProfile.isIdeaCreator)
    }

    @Test
    fun `isIdeaCreator should return false when idea_creator role not present`() {
        // Arrange
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("member", "moderator")
        )

        // Assert
        assertFalse(tokenProfile.isIdeaCreator)
    }

    @Test
    fun `isBanned should return true when banned role present`() {
        // Arrange
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("banned")
        )

        // Assert
        assertTrue(tokenProfile.isBanned)
    }

    @Test
    fun `isBanned should return false when banned role not present`() {
        // Arrange
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("member", "moderator")
        )

        // Assert
        assertFalse(tokenProfile.isBanned)
    }

    // ===============================
    // Multiple Role Combination Tests
    // ===============================

    @Test
    fun `should handle multiple privilege roles correctly`() {
        // Arrange
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("superadmin", "moderator", "idea_creator")
        )

        // Assert
        assertTrue(tokenProfile.isSuperAdmin)
        assertTrue(tokenProfile.isModerator)
        assertTrue(tokenProfile.isIdeaCreator)
        assertFalse(tokenProfile.isBanned)
    }

    @Test
    fun `should handle banned user with other roles`() {
        // Arrange - Banned users should not have other privileges
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("banned", "member")
        )

        // Assert
        assertTrue(tokenProfile.isBanned)
        assertFalse(tokenProfile.isSuperAdmin)
        assertFalse(tokenProfile.isModerator)
        assertFalse(tokenProfile.isIdeaCreator)
    }

    // ===============================
    // Edge Case and Boundary Tests
    // ===============================

    @Test
    fun `should handle role case sensitivity correctly`() {
        // Arrange - Test that role matching is case-sensitive
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("SUPERADMIN", "Moderator", "idea_Creator")
        )

        // Assert - Should be case-sensitive, so these should be false
        assertFalse(tokenProfile.isSuperAdmin)
        assertFalse(tokenProfile.isModerator)
        assertFalse(tokenProfile.isIdeaCreator)
    }

    @Test
    fun `should handle duplicate roles in list`() {
        // Arrange
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("superadmin", "superadmin", "member", "superadmin")
        )

        // Assert
        assertTrue(tokenProfile.isSuperAdmin)
        assertEquals(4, tokenProfile.roles.size) // Should preserve duplicates
    }

    @Test
    fun `should handle null-like string roles`() {
        // Arrange
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("", "null", "undefined")
        )

        // Assert
        assertFalse(tokenProfile.isSuperAdmin)
        assertFalse(tokenProfile.isModerator)
        assertFalse(tokenProfile.isIdeaCreator)
        assertFalse(tokenProfile.isBanned)
    }

    // ===============================
    // User Interface Implementation Tests
    // ===============================

    @Test
    fun `should implement User interface correctly`() {
        // Arrange
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = emptyList()
        )

        // Assert
        assertEquals(TEST_USER_ID, (tokenProfile as User).id)
    }

    // ===============================
    // Data Class Behavior Tests
    // ===============================

    @Test
    fun `should support data class equality correctly`() {
        // Arrange
        val tokenProfile1 = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("member")
        )

        val tokenProfile2 = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("member")
        )

        val tokenProfile3 = TokenProfile(
            id = TEST_USER_ID + 1,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("member")
        )

        // Assert
        assertEquals(tokenProfile1, tokenProfile2)
        assertNotEquals(tokenProfile1, tokenProfile3)
        assertEquals(tokenProfile1.hashCode(), tokenProfile2.hashCode())
    }

    @Test
    fun `should support data class copy functionality`() {
        // Arrange
        val originalProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = listOf("member")
        )

        // Act
        val copiedProfile = originalProfile.copy(
            displayName = "Updated Display Name",
            roles = listOf("member", "moderator")
        )

        // Assert
        assertEquals(TEST_USER_ID, copiedProfile.id)
        assertEquals(TEST_UUID, copiedProfile.uuid)
        assertEquals(TEST_MINECRAFT_USERNAME, copiedProfile.minecraftUsername)
        assertEquals("Updated Display Name", copiedProfile.displayName)
        assertEquals(listOf("member", "moderator"), copiedProfile.roles)

        // Original should be unchanged
        assertEquals(TEST_DISPLAY_NAME, originalProfile.displayName)
        assertEquals(listOf("member"), originalProfile.roles)
    }
}
