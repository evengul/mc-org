package app.mcorg.domain.model.user

import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.*

/**
 * Unit tests for User interface and its implementations.
 *
 * Tests the User interface hierarchy including:
 * - WorldMember implementation (world-scoped user data)
 * - Profile implementation (user account data)
 * - TokenProfile implementation (authentication data)
 * - Interface contract compliance and polymorphism
 * - Property access patterns and data integrity
 *
 * Priority: High (Foundation for user management across the application)
 */
class UserTest {

    // Test data constants
    companion object {
        private const val TEST_USER_ID = 42
        private const val TEST_WORLD_ID = 100
        private const val TEST_DISPLAY_NAME = "Test Player"
        private const val TEST_EMAIL = "test@example.com"
        private const val TEST_UUID = "550e8400-e29b-41d4-a716-446655440000"
        private const val TEST_MINECRAFT_USERNAME = "TestPlayer"
        private val TEST_TIMESTAMP = ZonedDateTime.now()
    }

    // ===============================
    // User Interface Contract Tests
    // ===============================

    @Test
    fun `all User implementations should provide id property`() {
        // Arrange
        val worldMember = createTestWorldMember()
        val profile = createTestProfile()
        val tokenProfile = createTestTokenProfile()

        // Act & Assert
        assertEquals(TEST_USER_ID, worldMember.id)
        assertEquals(TEST_USER_ID, profile.id)
        assertEquals(TEST_USER_ID, tokenProfile.id)
    }

    @Test
    fun `User implementations should be polymorphic`() {
        // Arrange
        val users: List<User> = listOf(
            createTestWorldMember(),
            createTestProfile(),
            createTestTokenProfile()
        )

        // Act & Assert
        users.forEach { user ->
            assertEquals(TEST_USER_ID, user.id)
        }
    }

    // ===============================
    // WorldMember Implementation Tests
    // ===============================

    @Test
    fun `WorldMember should implement User correctly`() {
        // Act
        val worldMember = createTestWorldMember()

        // Assert
        assertEquals(TEST_USER_ID, worldMember.id)
        assertEquals(TEST_WORLD_ID, worldMember.worldId)
        assertEquals(TEST_DISPLAY_NAME, worldMember.displayName)
        assertEquals(Role.MEMBER, worldMember.worldRole)
        assertEquals(TEST_TIMESTAMP, worldMember.createdAt)
        assertEquals(TEST_TIMESTAMP, worldMember.updatedAt)
    }

    @Test
    fun `WorldMember should support all role types`() {
        // Test all role types
        Role.entries.forEach { role ->
            // Act
            val worldMember = WorldMember(
                id = TEST_USER_ID,
                worldId = TEST_WORLD_ID,
                displayName = TEST_DISPLAY_NAME,
                worldRole = role,
                createdAt = TEST_TIMESTAMP,
                updatedAt = TEST_TIMESTAMP
            )

            // Assert
            assertEquals(role, worldMember.worldRole)
        }
    }

    @Test
    fun `WorldMember should handle different world IDs`() {
        // Arrange
        val worldIds = listOf(1, 999, 50000)

        worldIds.forEach { worldId ->
            // Act
            val worldMember = WorldMember(
                id = TEST_USER_ID,
                worldId = worldId,
                displayName = TEST_DISPLAY_NAME,
                worldRole = Role.MEMBER,
                createdAt = TEST_TIMESTAMP,
                updatedAt = TEST_TIMESTAMP
            )

            // Assert
            assertEquals(worldId, worldMember.worldId)
        }
    }

    @Test
    fun `WorldMember should support data class operations`() {
        // Arrange
        val worldMember1 = createTestWorldMember()
        val worldMember2 = createTestWorldMember()
        val worldMember3 = worldMember1.copy(displayName = "Different Name")

        // Assert
        assertEquals(worldMember1, worldMember2)
        assertNotEquals(worldMember1, worldMember3)
        assertEquals(worldMember1.hashCode(), worldMember2.hashCode())

        // Test copy functionality
        assertEquals(TEST_USER_ID, worldMember3.id)
        assertEquals("Different Name", worldMember3.displayName)
        assertEquals(TEST_DISPLAY_NAME, worldMember1.displayName) // Original unchanged
    }

    // ===============================
    // Profile Implementation Tests
    // ===============================

    @Test
    fun `Profile should implement User correctly`() {
        // Act
        val profile = createTestProfile()

        // Assert
        assertEquals(TEST_USER_ID, profile.id)
        assertEquals(TEST_EMAIL, profile.email)
    }

    @Test
    fun `Profile should have correct connection properties`() {
        // Act
        val profile = createTestProfile()

        // Assert
        assertFalse(profile.discordConnection)
        assertTrue(profile.microsoftConnection)
        assertNull(profile.avatarUrl)
    }

    @Test
    fun `Profile should handle different email formats`() {
        // Arrange
        val emailFormats = listOf(
            "simple@example.com",
            "user.name@example.com",
            "user+tag@example.com",
            "user@subdomain.example.com",
            "a@b.co"
        )

        emailFormats.forEach { email ->
            // Act
            val profile = Profile(
                id = TEST_USER_ID,
                email = email
            )

            // Assert
            assertEquals(email, profile.email)
        }
    }

    @Test
    fun `Profile should support data class operations`() {
        // Arrange
        val profile1 = createTestProfile()
        val profile2 = createTestProfile()
        val profile3 = profile1.copy(email = "different@example.com")

        // Assert
        assertEquals(profile1, profile2)
        assertNotEquals(profile1, profile3)
        assertEquals(profile1.hashCode(), profile2.hashCode())

        // Test copy functionality
        assertEquals(TEST_USER_ID, profile3.id)
        assertEquals("different@example.com", profile3.email)
        assertEquals(TEST_EMAIL, profile1.email) // Original unchanged
    }

    // ===============================
    // TokenProfile Implementation Tests
    // ===============================

    @Test
    fun `TokenProfile should implement User correctly`() {
        // Act
        val tokenProfile = createTestTokenProfile()

        // Assert
        assertEquals(TEST_USER_ID, tokenProfile.id)
        assertEquals(TEST_UUID, tokenProfile.uuid)
        assertEquals(TEST_MINECRAFT_USERNAME, tokenProfile.minecraftUsername)
        assertEquals(TEST_DISPLAY_NAME, tokenProfile.displayName)
    }

    @Test
    fun `TokenProfile should handle role-based properties correctly`() {
        // Test different role combinations
        val testCases = listOf(
            listOf("superadmin") to arrayOf(true, true, true, false),
            listOf("moderator") to arrayOf(false, true, false, false),
            listOf("idea_creator") to arrayOf(false, false, true, false),
            listOf("banned") to arrayOf(false, false, false, true),
            listOf("superadmin", "moderator") to arrayOf(true, true, true, false),
            emptyList<String>() to arrayOf(false, false, false, false)
        )

        testCases.forEach { (roles, expected) ->
            // Act
            val tokenProfile = TokenProfile(
                id = TEST_USER_ID,
                uuid = TEST_UUID,
                minecraftUsername = TEST_MINECRAFT_USERNAME,
                displayName = TEST_DISPLAY_NAME,
                roles = roles
            )

            // Assert
            assertEquals(expected[0], tokenProfile.isSuperAdmin, "isSuperAdmin failed for roles: $roles")
            assertEquals(expected[1], tokenProfile.isModerator, "isModerator failed for roles: $roles")
            assertEquals(expected[2], tokenProfile.isIdeaCreator, "isIdeaCreator failed for roles: $roles")
            assertEquals(expected[3], tokenProfile.isBanned, "isBanned failed for roles: $roles")
        }
    }

    // ===============================
    // Polymorphism and Type Tests
    // ===============================

    @Test
    fun `should handle User interface polymorphically`() {
        // Arrange
        val users = listOf<User>(
            createTestWorldMember(),
            createTestProfile(),
            createTestTokenProfile()
        )

        // Act & Assert
        users.forEach { user ->
            when (user) {
                is WorldMember -> {
                    assertEquals(TEST_WORLD_ID, user.worldId)
                    assertEquals(Role.MEMBER, user.worldRole)
                }
                is Profile -> {
                    assertEquals(TEST_EMAIL, user.email)
                    assertTrue(user.microsoftConnection)
                }
                is TokenProfile -> {
                    assertEquals(TEST_UUID, user.uuid)
                    assertEquals(TEST_MINECRAFT_USERNAME, user.minecraftUsername)
                }
            }
        }
    }

    // ===============================
    // Boundary and Edge Case Tests
    // ===============================

    @Test
    fun `should handle extreme user ID values`() {
        // Test boundary conditions for user IDs
        val extremeIds = listOf(1, Int.MAX_VALUE, 0)

        extremeIds.forEach { id ->
            // Act
            val users = listOf<User>(
                WorldMember(id, TEST_WORLD_ID, TEST_DISPLAY_NAME, Role.MEMBER, TEST_TIMESTAMP, TEST_TIMESTAMP),
                Profile(id, TEST_EMAIL),
                TokenProfile(id, TEST_UUID, TEST_MINECRAFT_USERNAME, TEST_DISPLAY_NAME, emptyList())
            )

            // Assert
            users.forEach { user ->
                assertEquals(id, user.id)
            }
        }
    }

    @Test
    fun `should handle timestamp edge cases in WorldMember`() {
        // Arrange
        val pastTimestamp = ZonedDateTime.now().minusYears(5)
        val futureTimestamp = ZonedDateTime.now().plusYears(5)

        // Act
        val worldMemberPast = WorldMember(
            id = TEST_USER_ID,
            worldId = TEST_WORLD_ID,
            displayName = TEST_DISPLAY_NAME,
            worldRole = Role.MEMBER,
            createdAt = pastTimestamp,
            updatedAt = pastTimestamp
        )

        val worldMemberFuture = WorldMember(
            id = TEST_USER_ID,
            worldId = TEST_WORLD_ID,
            displayName = TEST_DISPLAY_NAME,
            worldRole = Role.MEMBER,
            createdAt = futureTimestamp,
            updatedAt = futureTimestamp
        )

        // Assert
        assertEquals(pastTimestamp, worldMemberPast.createdAt)
        assertEquals(pastTimestamp, worldMemberPast.updatedAt)
        assertEquals(futureTimestamp, worldMemberFuture.createdAt)
        assertEquals(futureTimestamp, worldMemberFuture.updatedAt)
    }

    // ===============================
    // Integration Pattern Tests
    // ===============================

    @Test
    fun `should work correctly in authentication context`() {
        // Simulate authentication flow
        val minecraftProfile = MinecraftProfile(TEST_UUID, TEST_MINECRAFT_USERNAME)

        // Act - Convert to TokenProfile (typical authentication pattern)
        val tokenProfile = TokenProfile(
            id = TEST_USER_ID,
            uuid = minecraftProfile.uuid,
            minecraftUsername = minecraftProfile.username,
            displayName = minecraftProfile.username, // Default display name
            roles = listOf("member")
        )

        // Assert
        assertEquals(minecraftProfile.uuid, tokenProfile.uuid)
        assertEquals(minecraftProfile.username, tokenProfile.minecraftUsername)
    }

    @Test
    fun `should work correctly in world membership context`() {
        // Simulate world invitation acceptance
        val tokenProfile = createTestTokenProfile()

        // Act - Convert to WorldMember (typical invitation acceptance pattern)
        val worldMember = WorldMember(
            id = tokenProfile.id,
            worldId = TEST_WORLD_ID,
            displayName = tokenProfile.displayName,
            worldRole = Role.MEMBER,
            createdAt = TEST_TIMESTAMP,
            updatedAt = TEST_TIMESTAMP
        )

        // Assert
        assertEquals(tokenProfile.id, worldMember.id)
        assertEquals(tokenProfile.displayName, worldMember.displayName)
    }

    // ===============================
    // Helper Methods
    // ===============================

    private fun createTestWorldMember(): WorldMember {
        return WorldMember(
            id = TEST_USER_ID,
            worldId = TEST_WORLD_ID,
            displayName = TEST_DISPLAY_NAME,
            worldRole = Role.MEMBER,
            createdAt = TEST_TIMESTAMP,
            updatedAt = TEST_TIMESTAMP
        )
    }

    private fun createTestProfile(): Profile {
        return Profile(
            id = TEST_USER_ID,
            email = TEST_EMAIL
        )
    }

    private fun createTestTokenProfile(): TokenProfile {
        return TokenProfile(
            id = TEST_USER_ID,
            uuid = TEST_UUID,
            minecraftUsername = TEST_MINECRAFT_USERNAME,
            displayName = TEST_DISPLAY_NAME,
            roles = emptyList()
        )
    }
}
