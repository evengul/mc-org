package app.mcorg.pipeline.auth

import app.mcorg.config.Database
import app.mcorg.config.DatabaseConnectionProvider
import app.mcorg.domain.model.user.MinecraftProfile
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.CreateUserIfNotExistsFailure
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("SqlSourceToSinkFlow")
class CreateUserIfNotExistsStepTest {

    private val mockConnection = mockk<Connection>()
    private val mockCheckUserStatement = mockk<PreparedStatement>()
    private val mockUpdateUsernameStatement = mockk<PreparedStatement>()
    private val mockUpdateWorldMembersStatement = mockk<PreparedStatement>()
    private val mockCreateUserStatement = mockk<PreparedStatement>()
    private val mockCreateProfileStatement = mockk<PreparedStatement>()
    private val mockResultSet = mockk<ResultSet>()
    private val mockProvider = mockk<DatabaseConnectionProvider>()

    private val testProfile = MinecraftProfile(
        uuid = "test-uuid-123",
        username = "TestPlayer"
    )

    @BeforeEach
    fun setup() {
        Database.setProvider(mockProvider)
        every { mockProvider.getConnection() } returns mockConnection
        every { mockConnection.close() } just Runs

        // Mock transaction-related methods
        every { mockConnection.setAutoCommit(any()) } just Runs
        every { mockConnection.commit() } just Runs
        every { mockConnection.rollback() } just Runs

        // Set up common mock behaviors
        every { mockCheckUserStatement.close() } just Runs
        every { mockUpdateUsernameStatement.close() } just Runs
        every { mockUpdateWorldMembersStatement.close() } just Runs
        every { mockCreateUserStatement.close() } just Runs
        every { mockCreateProfileStatement.close() } just Runs
        every { mockResultSet.close() } just Runs

        // Mock parameter setting methods
        every { mockCheckUserStatement.setString(any(), any()) } just Runs
        every { mockUpdateUsernameStatement.setString(any(), any()) } just Runs
        every { mockUpdateUsernameStatement.setInt(any(), any()) } just Runs
        every { mockUpdateWorldMembersStatement.setString(any(), any()) } just Runs
        every { mockUpdateWorldMembersStatement.setInt(any(), any()) } just Runs
        every { mockCreateUserStatement.setString(any(), any()) } just Runs
        every { mockCreateProfileStatement.setInt(any(), any()) } just Runs
        every { mockCreateProfileStatement.setString(any(), any()) } just Runs
    }

    @AfterEach
    fun teardown() {
        Database.resetProvider()
        unmockkAll()
    }

    @Test
    fun `returns existing user when UUID exists and username matches`() = runBlocking {
        // Arrange
        val expectedUser = TokenProfile(
            id = 1,
            uuid = "test-uuid-123",
            minecraftUsername = "TestPlayer",
            displayName = "TestPlayer"
        )

        every { mockConnection.prepareStatement(match { it.contains("SELECT u.id, u.email, mp.uuid, mp.username") }) } returns mockCheckUserStatement
        every { mockCheckUserStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns true
        every { mockResultSet.getInt("id") } returns 1
        every { mockResultSet.getString("email") } returns "test@example.com"
        every { mockResultSet.getString("uuid") } returns "test-uuid-123"
        every { mockResultSet.getString("username") } returns "TestPlayer"

        // Act
        val result = CreateUserIfNotExistsStep.process(testProfile)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(expectedUser, result.value)
        verify { mockCheckUserStatement.setString(1, "test-uuid-123") }
        verify { mockCheckUserStatement.executeQuery() }
        verify(exactly = 0) { mockUpdateUsernameStatement.executeUpdate() }
    }

    @Test
    fun `updates username and world members when UUID exists but username differs`() = runBlocking {
        // Arrange
        val newUsername = "NewTestPlayer"
        val profileWithNewUsername = testProfile.copy(username = newUsername)

        // Mock check user query - returns existing user with old username
        every { mockConnection.prepareStatement(match { it.contains("SELECT u.id, u.email, mp.uuid, mp.username") }) } returns mockCheckUserStatement
        every { mockCheckUserStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns true
        every { mockResultSet.getInt("id") } returns 1
        every { mockResultSet.getString("email") } returns "test@example.com"
        every { mockResultSet.getString("uuid") } returns "test-uuid-123"
        every { mockResultSet.getString("username") } returns "TestPlayer" // Old username

        // Mock update username
        every { mockConnection.prepareStatement(match { it.contains("UPDATE minecraft_profiles") }) } returns mockUpdateUsernameStatement
        every { mockUpdateUsernameStatement.executeUpdate() } returns 1

        // Mock update world members
        every { mockConnection.prepareStatement(match { it.contains("UPDATE world_members") }) } returns mockUpdateWorldMembersStatement
        every { mockUpdateWorldMembersStatement.executeUpdate() } returns 2 // Updated 2 world member records

        // Act
        val result = CreateUserIfNotExistsStep.process(profileWithNewUsername)

        // Assert
        assertTrue(result is Result.Success)
        val tokenProfile = result.value
        assertEquals(1, tokenProfile.id)
        assertEquals("test-uuid-123", tokenProfile.uuid)
        assertEquals(newUsername, tokenProfile.minecraftUsername)
        assertEquals(newUsername, tokenProfile.displayName)

        // Verify username update
        verify { mockUpdateUsernameStatement.setString(1, newUsername) }
        verify { mockUpdateUsernameStatement.setString(2, "test-uuid-123") }
        verify { mockUpdateUsernameStatement.executeUpdate() }

        // Verify world members update
        verify { mockUpdateWorldMembersStatement.setString(1, newUsername) }
        verify { mockUpdateWorldMembersStatement.setInt(2, 1) }
        verify { mockUpdateWorldMembersStatement.setString(3, "TestPlayer") }
        verify { mockUpdateWorldMembersStatement.executeUpdate() }
    }

    @Test
    fun `continues successfully even when world members update fails`() = runBlocking {
        // Arrange
        val newUsername = "NewTestPlayer"
        val profileWithNewUsername = testProfile.copy(username = newUsername)

        // Mock check user query - returns existing user with old username
        every { mockConnection.prepareStatement(match { it.contains("SELECT u.id, u.email, mp.uuid, mp.username") }) } returns mockCheckUserStatement
        every { mockCheckUserStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns true
        every { mockResultSet.getInt("id") } returns 1
        every { mockResultSet.getString("email") } returns "test@example.com"
        every { mockResultSet.getString("uuid") } returns "test-uuid-123"
        every { mockResultSet.getString("username") } returns "TestPlayer"

        // Mock update username - succeeds
        every { mockConnection.prepareStatement(match { it.contains("UPDATE minecraft_profiles") }) } returns mockUpdateUsernameStatement
        every { mockUpdateUsernameStatement.executeUpdate() } returns 1

        // Mock update world members - fails
        every { mockConnection.prepareStatement(match { it.contains("UPDATE world_members") }) } returns mockUpdateWorldMembersStatement
        every { mockUpdateWorldMembersStatement.executeUpdate() } throws SQLException("World members update failed")

        // Act
        val result = CreateUserIfNotExistsStep.process(profileWithNewUsername)

        // Assert
        assertTrue(result is Result.Success)
        val tokenProfile = result.value
        assertEquals(newUsername, tokenProfile.minecraftUsername)

        verify { mockUpdateUsernameStatement.executeUpdate() }
        verify { mockUpdateWorldMembersStatement.executeUpdate() }
    }

    @Test
    fun `creates new user when UUID does not exist`() = runBlocking {
        // Arrange - no existing user
        every { mockConnection.prepareStatement(match { it.contains("SELECT u.id, u.email, mp.uuid, mp.username") }) } returns mockCheckUserStatement
        every { mockCheckUserStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns false // No existing user found

        // Mock transaction behavior - create user (use a different result set for the INSERT)
        val mockUserInsertResultSet = mockk<ResultSet>()
        every { mockUserInsertResultSet.close() } just Runs
        every { mockConnection.prepareStatement(match { it.contains("INSERT INTO users") }) } returns mockCreateUserStatement
        every { mockCreateUserStatement.executeQuery() } returns mockUserInsertResultSet
        every { mockUserInsertResultSet.next() } returns true
        every { mockUserInsertResultSet.getInt("id") } returns 2

        // Mock create minecraft profile
        every { mockConnection.prepareStatement(match { it.contains("INSERT INTO minecraft_profiles") }) } returns mockCreateProfileStatement
        every { mockCreateProfileStatement.executeUpdate() } returns 1

        // Act
        val result = CreateUserIfNotExistsStep.process(testProfile)

        // Assert
        assertTrue(result is Result.Success)
        val tokenProfile = result.value
        assertEquals(2, tokenProfile.id)
        assertEquals("test-uuid-123", tokenProfile.uuid)
        assertEquals("TestPlayer", tokenProfile.minecraftUsername)
        assertEquals("TestPlayer", tokenProfile.displayName)

        verify { mockCreateUserStatement.setString(1, "test-uuid-123@minecraft.temp") }
        verify { mockCreateUserStatement.executeQuery() }
        verify { mockCreateProfileStatement.setInt(1, 2) }
        verify { mockCreateProfileStatement.setString(2, "test-uuid-123") }
        verify { mockCreateProfileStatement.setString(3, "TestPlayer") }
        verify { mockCreateProfileStatement.executeUpdate() }
    }

    @Test
    fun `returns failure when user check query fails`() = runBlocking {
        // Arrange
        val sqlException = SQLException("Database connection failed")
        every { mockConnection.prepareStatement(match { it.contains("SELECT u.id, u.email, mp.uuid, mp.username") }) } throws sqlException

        // Act
        val result = CreateUserIfNotExistsStep.process(testProfile)

        // Assert
        assertTrue(result is Result.Failure)
        assertTrue(result.error is CreateUserIfNotExistsFailure.Other)
    }

    @Test
    fun `returns failure when username update fails`() = runBlocking {
        // Arrange
        val newUsername = "NewTestPlayer"
        val profileWithNewUsername = testProfile.copy(username = newUsername)

        // Mock check user query - returns existing user with different username
        every { mockConnection.prepareStatement(match { it.contains("SELECT u.id, u.email, mp.uuid, mp.username") }) } returns mockCheckUserStatement
        every { mockCheckUserStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns true
        every { mockResultSet.getInt("id") } returns 1
        every { mockResultSet.getString("email") } returns "test@example.com"
        every { mockResultSet.getString("uuid") } returns "test-uuid-123"
        every { mockResultSet.getString("username") } returns "TestPlayer"

        // Mock update username - fails
        val sqlException = SQLException("Update failed")
        every { mockConnection.prepareStatement(match { it.contains("UPDATE minecraft_profiles") }) } throws sqlException

        // Act
        val result = CreateUserIfNotExistsStep.process(profileWithNewUsername)

        // Assert
        assertTrue(result is Result.Failure)
        assertTrue(result.error is CreateUserIfNotExistsFailure.Other)
    }

    @Test
    fun `returns failure when username update returns zero affected rows`() = runBlocking {
        // Arrange
        val newUsername = "NewTestPlayer"
        val profileWithNewUsername = testProfile.copy(username = newUsername)

        // Mock check user query
        every { mockConnection.prepareStatement(match { it.contains("SELECT u.id, u.email, mp.uuid, mp.username") }) } returns mockCheckUserStatement
        every { mockCheckUserStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns true
        every { mockResultSet.getInt("id") } returns 1
        every { mockResultSet.getString("email") } returns "test@example.com"
        every { mockResultSet.getString("uuid") } returns "test-uuid-123"
        every { mockResultSet.getString("username") } returns "TestPlayer"

        // Mock update username - returns 0 affected rows
        every { mockConnection.prepareStatement(match { it.contains("UPDATE minecraft_profiles") }) } returns mockUpdateUsernameStatement
        every { mockUpdateUsernameStatement.executeUpdate() } returns 0

        // Act
        val result = CreateUserIfNotExistsStep.process(profileWithNewUsername)

        // Assert
        assertTrue(result is Result.Failure)
        assertTrue(result.error is CreateUserIfNotExistsFailure.Other)
    }

    @Test
    fun `returns failure when user creation fails`() = runBlocking {
        // Arrange - no existing user
        every { mockConnection.prepareStatement(match { it.contains("SELECT u.id, u.email, mp.uuid, mp.username") }) } returns mockCheckUserStatement
        every { mockCheckUserStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns false

        // Mock user creation - fails
        val sqlException = SQLException("User creation failed")
        every { mockConnection.prepareStatement(match { it.contains("INSERT INTO users") }) } throws sqlException

        // Act
        val result = CreateUserIfNotExistsStep.process(testProfile)

        // Assert
        assertTrue(result is Result.Failure)
        assertTrue(result.error is CreateUserIfNotExistsFailure.Other)
    }

    @Test
    fun `returns failure when minecraft profile creation fails`() = runBlocking {
        // Arrange - no existing user
        every { mockConnection.prepareStatement(match { it.contains("SELECT u.id, u.email, mp.uuid, mp.username") }) } returns mockCheckUserStatement
        every { mockCheckUserStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns false

        // Mock user creation - succeeds
        every { mockConnection.prepareStatement(match { it.contains("INSERT INTO users") }) } returns mockCreateUserStatement
        every { mockCreateUserStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns true
        every { mockResultSet.getInt("id") } returns 2

        // Mock minecraft profile creation - fails
        val sqlException = SQLException("Profile creation failed")
        every { mockConnection.prepareStatement(match { it.contains("INSERT INTO minecraft_profiles") }) } throws sqlException

        // Act
        val result = CreateUserIfNotExistsStep.process(testProfile)

        // Assert
        assertTrue(result is Result.Failure)
        assertTrue(result.error is CreateUserIfNotExistsFailure.Other)
    }

    @Test
    fun `returns failure when minecraft profile creation returns zero affected rows`() = runBlocking {
        // Arrange - no existing user
        every { mockConnection.prepareStatement(match { it.contains("SELECT u.id, u.email, mp.uuid, mp.username") }) } returns mockCheckUserStatement
        every { mockCheckUserStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns false

        // Mock user creation - succeeds
        every { mockConnection.prepareStatement(match { it.contains("INSERT INTO users") }) } returns mockCreateUserStatement
        every { mockCreateUserStatement.executeQuery() } returns mockResultSet
        every { mockResultSet.next() } returns true
        every { mockResultSet.getInt("id") } returns 2

        // Mock minecraft profile creation - returns 0 affected rows
        every { mockConnection.prepareStatement(match { it.contains("INSERT INTO minecraft_profiles") }) } returns mockCreateProfileStatement
        every { mockCreateProfileStatement.executeUpdate() } returns 0

        // Act
        val result = CreateUserIfNotExistsStep.process(testProfile)

        // Assert
        assertTrue(result is Result.Failure)
        assertTrue(result.error is CreateUserIfNotExistsFailure.Other)
    }
}
