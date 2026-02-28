package app.mcorg.test.fixtures

import app.mcorg.domain.model.user.*

/**
 * Factory for creating test data objects with realistic defaults.
 *
 * This provides consistent test data across all test files while allowing
 * customization of specific fields as needed for individual tests.
 */
object TestDataFactory {

    /**
     * Create a test TokenProfile for authenticated user scenarios
     */
    fun createTestTokenProfile(
        id: Int = 1,
        uuid: String = "550e8400-e29b-41d4-a716-446655440000",
        minecraftUsername: String = "TestPlayer",
        displayName: String = "Test User",
        roles: List<String> = emptyList()
    ): TokenProfile = TokenProfile(
        id = id,
        uuid = uuid,
        minecraftUsername = minecraftUsername,
        displayName = displayName,
        roles = roles
    )

    /**
     * Create a test MinecraftProfile for authentication flows
     */
    fun createTestMinecraftProfile(
        uuid: String = "550e8400-e29b-41d4-a716-446655440000",
        username: String = "TestPlayer"
    ): MinecraftProfile = MinecraftProfile(
        uuid = uuid,
        username = username
    )

    // Note: Other domain entities like World, Project, Task would need to be examined
    // from the actual domain model files to ensure correct property names and types.
    // The previous implementations made assumptions about the structure that don't match
    // the actual codebase.
}
