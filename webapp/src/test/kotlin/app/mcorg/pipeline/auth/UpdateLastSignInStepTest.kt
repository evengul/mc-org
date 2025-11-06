package app.mcorg.pipeline.auth

import app.mcorg.config.Database
import app.mcorg.config.DatabaseConnectionProvider
import app.mcorg.pipeline.auth.commonsteps.UpdateLastSignInStep
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.test.utils.TestUtils
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLTimeoutException
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for UpdateLastSignInStep covering database operations for login timestamp updates.
 * Tests the complete last login update pipeline including success scenarios, error handling, and edge cases.
 */
class UpdateLastSignInStepTest {

    private val mockConnection = mockk<Connection>()
    private val mockProvider = mockk<DatabaseConnectionProvider>()
    private val mockStatement = mockk<PreparedStatement>()
    private val mockResultResult = mockk<ResultSet>()

    @BeforeEach
    fun setup() {
        clearAllMocks()

        Database.setProvider(mockProvider)
        every { mockProvider.getConnection() } returns mockConnection
        every { mockConnection.close() } just Runs
        every { mockStatement.close() } just Runs
        every { mockResultResult.close() } just Runs
        every { mockStatement.setString(any(), any()) } just Runs
        every { mockStatement.executeUpdate() } returns 1

        every { mockConnection.prepareStatement(any()) } returns mockStatement
    }

    companion object {
        private const val TEST_USERNAME = "testuser"
    }

    // =====================================
    // Successful Update Tests
    // =====================================

    @Test
    fun `should successfully update last login for existing user`() {
        // Arrange
        val username = TEST_USERNAME

        // Act
        TestUtils.executeAndAssertSuccess(UpdateLastSignInStep, username)

        // Verify database call was made with correct parameters
        verify { mockStatement.setString(1, username) }
    }

    @Test
    fun `should handle multiple rapid login updates correctly`() {
        // Arrange
        val username = TEST_USERNAME

        // Act - Simulate rapid consecutive logins
        TestUtils.executeAndAssertSuccess(UpdateLastSignInStep, username)
        TestUtils.executeAndAssertSuccess(UpdateLastSignInStep, username)
        TestUtils.executeAndAssertSuccess(UpdateLastSignInStep, username)

        // Verify database was called 3 times
        verify(exactly = 3) {
            mockStatement.setString(1, username)
        }
    }

    // =====================================
    // Error Recovery Tests
    // =====================================

    @Test
    fun `should fail gracefully when database is unavailable`() {
        // Arrange
        val username = TEST_USERNAME

        // Mock database unavailable scenario
        every { mockProvider.getConnection() } throws SQLTimeoutException("Database down")

        // Act & Assert
        val error = TestUtils.executeAndAssertFailure(
            UpdateLastSignInStep,
            username
        )

        assertTrue(error is AppFailure.DatabaseError.ConnectionError)

        // Verify the step attempted the database call and failed gracefully
        verify {
            mockProvider.getConnection()
        }
    }
}
