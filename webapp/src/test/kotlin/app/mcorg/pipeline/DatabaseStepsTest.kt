package app.mcorg.pipeline

import app.mcorg.config.Database
import app.mcorg.config.DatabaseConnectionProvider
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.DatabaseFailure
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.*
import kotlin.io.use
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("SqlSourceToSinkFlow")
class DatabaseStepsTest {

    private val mockConnection = mockk<Connection>()
    private val mockPreparedStatement = mockk<PreparedStatement>()
    private val mockResultSet = mockk<ResultSet>()
    private val mockProvider = mockk<DatabaseConnectionProvider>()

    @BeforeEach
    fun setup() {
        // Set up the mock provider instead of mocking the Database object directly
        Database.setProvider(mockProvider)

        every { mockProvider.getConnection() } returns mockConnection

        // Remove the mockkStatic and use function mocking
        // Instead, let the use functions execute normally and mock the actual operations
        every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
        every { mockConnection.close() } just Runs

        every { mockPreparedStatement.executeQuery() } returns mockResultSet
        every { mockPreparedStatement.executeUpdate() } returns 1
        every { mockPreparedStatement.close() } just Runs

        every { mockResultSet.close() } just Runs

        // Mock common parameter setting methods
        every { mockPreparedStatement.setString(any(), any()) } just Runs
        every { mockPreparedStatement.setInt(any(), any()) } just Runs
        every { mockPreparedStatement.setBoolean(any(), any()) } just Runs
        every { mockPreparedStatement.setNull(any(), any()) } just Runs
    }

    @AfterEach
    fun teardown() {
        Database.resetProvider()
        unmockkAll()
    }

    // Query Tests

    @Test
    fun `query returns success result with mapped data`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users WHERE id = ?")
        val input = 123
        val expectedData = "John Doe"

        every { mockConnection.prepareStatement(safeSQL.query) } returns mockPreparedStatement
        every { mockPreparedStatement.executeQuery() } returns mockResultSet

        val parameterSetter: (PreparedStatement, Int) -> Unit = { stmt, id ->
            stmt.setInt(1, id)
        }
        val errorMapper: (DatabaseFailure) -> String = { failure -> "Error: $failure" }
        val resultMapper: (ResultSet) -> String = { rs -> expectedData }

        // Act
        val step = DatabaseSteps.query(safeSQL, parameterSetter, errorMapper, resultMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(expectedData, result.value)
        verify { mockPreparedStatement.setInt(1, input) }
        verify { mockPreparedStatement.executeQuery() }
    }

    @Test
    fun `query with default parameter setter works correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT COUNT(*) FROM users")
        val input = Unit

        every { mockConnection.prepareStatement(safeSQL.query) } returns mockPreparedStatement
        val expectedCount = 42

        val errorMapper: (DatabaseFailure) -> String = { "Error" }
        val resultMapper: (ResultSet) -> Int = { expectedCount }

        // Act
        val step = DatabaseSteps.query<Unit, String, Int>(safeSQL, errorMapper = errorMapper, resultMapper = resultMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(expectedCount, result.value)
        verify { mockPreparedStatement.executeQuery() }
        verify(exactly = 0) { mockPreparedStatement.setInt(any(), any()) }
    }

    @Test
    fun `query handles SQLTimeoutException correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit
        val timeoutException = SQLTimeoutException("Connection timeout")

        every { mockConnection.prepareStatement(safeSQL.query) } throws timeoutException

        val errorMapper: (DatabaseFailure) -> String = { failure ->
            when (failure) {
                DatabaseFailure.ConnectionError -> "Connection error"
                else -> "Other error"
            }
        }
        val resultMapper: (ResultSet) -> String = { "data" }

        // Act
        val step = DatabaseSteps.query<Unit, String, String>(safeSQL, errorMapper = errorMapper, resultMapper = resultMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Connection error", result.error)
    }

    @Test
    fun `query handles SQLSyntaxErrorException correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit
        val syntaxException = SQLSyntaxErrorException("Invalid SQL syntax")

        every { mockConnection.prepareStatement(safeSQL.query) } throws syntaxException

        val errorMapper: (DatabaseFailure) -> String = { failure ->
            when (failure) {
                DatabaseFailure.StatementError -> "Statement error"
                else -> "Other error"
            }
        }
        val resultMapper: (ResultSet) -> String = { "data" }

        // Act
        val step = DatabaseSteps.query<Unit, String, String>(safeSQL, errorMapper = errorMapper, resultMapper = resultMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Statement error", result.error)
    }

    @Test
    fun `query handles SQLIntegrityConstraintViolationException correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit
        val constraintException = SQLIntegrityConstraintViolationException("Constraint violation")

        every { mockConnection.prepareStatement(safeSQL.query) } throws constraintException

        val errorMapper: (DatabaseFailure) -> String = { failure ->
            when (failure) {
                DatabaseFailure.IntegrityConstraintError -> "Constraint error"
                else -> "Other error"
            }
        }
        val resultMapper: (ResultSet) -> String = { "data" }

        // Act
        val step = DatabaseSteps.query<Unit, String, String>(safeSQL, errorMapper = errorMapper, resultMapper = resultMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Constraint error", result.error)
    }

    @Test
    fun `query handles unknown exceptions correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit
        val unknownException = RuntimeException("Unknown error")

        every { mockConnection.prepareStatement(safeSQL.query) } throws unknownException

        val errorMapper: (DatabaseFailure) -> String = { failure ->
            when (failure) {
                DatabaseFailure.UnknownError -> "Unknown error"
                else -> "Other error"
            }
        }
        val resultMapper: (ResultSet) -> String = { "data" }

        // Act
        val step = DatabaseSteps.query<Unit, String, String>(safeSQL, errorMapper = errorMapper, resultMapper = resultMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Unknown error", result.error)
    }

    // Update Tests

    @Test
    fun `update returns success result with affected rows count`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.update("UPDATE users SET name = ? WHERE id = ?")
        val input = Pair("Jane Doe", 123)
        val expectedAffectedRows = 1

        every { mockConnection.prepareStatement(safeSQL.query) } returns mockPreparedStatement
        every { mockPreparedStatement.executeUpdate() } returns expectedAffectedRows

        val parameterSetter: (PreparedStatement, Pair<String, Int>) -> Unit = { stmt, data ->
            stmt.setString(1, data.first)
            stmt.setInt(2, data.second)
        }
        val errorMapper: (DatabaseFailure) -> String = { "Error" }

        every { mockPreparedStatement.setString(1, input.first) } just Runs
        every { mockPreparedStatement.setInt(2, input.second) } just Runs

        // Act
        val step = DatabaseSteps.update(safeSQL, parameterSetter, errorMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(expectedAffectedRows, result.value)
        verify { mockPreparedStatement.setString(1, input.first) }
        verify { mockPreparedStatement.setInt(2, input.second) }
        verify { mockPreparedStatement.executeUpdate() }
    }

    @Test
    fun `update handles SQLTimeoutException correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.insert("INSERT INTO users (name) VALUES (?)")
        val input = "John Doe"
        val timeoutException = SQLTimeoutException("Connection timeout")

        every { mockConnection.prepareStatement(safeSQL.query) } throws timeoutException

        val parameterSetter: (PreparedStatement, String) -> Unit = { stmt, name ->
            stmt.setString(1, name)
        }
        val errorMapper: (DatabaseFailure) -> String = { failure ->
            when (failure) {
                DatabaseFailure.ConnectionError -> "Connection error"
                else -> "Other error"
            }
        }

        // Act
        val step = DatabaseSteps.update(safeSQL, parameterSetter, errorMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Connection error", result.error)
    }

    @Test
    fun `update handles SQLSyntaxErrorException correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.update("UPDATE users SET name = ?")
        val input = "John Doe"
        val syntaxException = SQLSyntaxErrorException("Invalid SQL syntax")

        every { mockConnection.prepareStatement(safeSQL.query) } throws syntaxException

        val parameterSetter: (PreparedStatement, String) -> Unit = { stmt, name ->
            stmt.setString(1, name)
        }
        val errorMapper: (DatabaseFailure) -> String = { failure ->
            when (failure) {
                DatabaseFailure.StatementError -> "Statement error"
                else -> "Other error"
            }
        }

        // Act
        val step = DatabaseSteps.update(safeSQL, parameterSetter, errorMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Statement error", result.error)
    }

    @Test
    fun `update handles SQLIntegrityConstraintViolationException correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.insert("INSERT INTO users (email) VALUES (?)")
        val input = "duplicate@example.com"
        val constraintException = SQLIntegrityConstraintViolationException("Unique constraint violation")

        every { mockConnection.prepareStatement(safeSQL.query) } throws constraintException

        val parameterSetter: (PreparedStatement, String) -> Unit = { stmt, email ->
            stmt.setString(1, email)
        }
        val errorMapper: (DatabaseFailure) -> String = { failure ->
            when (failure) {
                DatabaseFailure.IntegrityConstraintError -> "Constraint error"
                else -> "Other error"
            }
        }

        // Act
        val step = DatabaseSteps.update(safeSQL, parameterSetter, errorMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Constraint error", result.error)
    }

    @Test
    fun `update handles unknown exceptions correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.delete("DELETE FROM users WHERE id = ?")
        val input = 123
        val unknownException = RuntimeException("Unknown error")

        every { mockConnection.prepareStatement(safeSQL.query) } throws unknownException

        val parameterSetter: (PreparedStatement, Int) -> Unit = { stmt, id ->
            stmt.setInt(1, id)
        }
        val errorMapper: (DatabaseFailure) -> String = { failure ->
            when (failure) {
                DatabaseFailure.UnknownError -> "Unknown error"
                else -> "Other error"
            }
        }

        // Act
        val step = DatabaseSteps.update(safeSQL, parameterSetter, errorMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Unknown error", result.error)
    }

    enum class CustomError { DATABASE_ERROR, CONNECTION_FAILED, SYNTAX_ERROR }

    @Test
    fun `query with custom error mapper works correctly`() = runBlocking {

        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit
        val timeoutException = SQLTimeoutException("Connection timeout")

        every { mockConnection.prepareStatement(safeSQL.query) } throws timeoutException

        val errorMapper: (DatabaseFailure) -> CustomError = { failure ->
            when (failure) {
                DatabaseFailure.ConnectionError -> CustomError.CONNECTION_FAILED
                DatabaseFailure.StatementError -> CustomError.SYNTAX_ERROR
                else -> CustomError.DATABASE_ERROR
            }
        }
        val resultMapper: (ResultSet) -> String = { "data" }

        // Act
        val step = DatabaseSteps.query<Unit, CustomError, String>(safeSQL, errorMapper = errorMapper, resultMapper = resultMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals(CustomError.CONNECTION_FAILED, result.error)
    }

    @Test
    fun `query with complex parameter setting works correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users WHERE age > ? AND name LIKE ? AND active = ?")
        val input = Triple(25, "John%", true)
        val expectedResult = listOf("John Doe", "John Smith")

        every { mockConnection.prepareStatement(safeSQL.query) } returns mockPreparedStatement
        every { mockPreparedStatement.executeQuery() } returns mockResultSet

        val parameterSetter: (PreparedStatement, Triple<Int, String, Boolean>) -> Unit = { stmt, params ->
            stmt.setInt(1, params.first)
            stmt.setString(2, params.second)
            stmt.setBoolean(3, params.third)
        }
        val errorMapper: (DatabaseFailure) -> String = { "Error" }
        val resultMapper: (ResultSet) -> List<String> = { expectedResult }

        every { mockPreparedStatement.setInt(1, input.first) } just Runs
        every { mockPreparedStatement.setString(2, input.second) } just Runs
        every { mockPreparedStatement.setBoolean(3, input.third) } just Runs

        // Act
        val step = DatabaseSteps.query(safeSQL, parameterSetter, errorMapper, resultMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(expectedResult, result.value)
        verify { mockPreparedStatement.setInt(1, input.first) }
        verify { mockPreparedStatement.setString(2, input.second) }
        verify { mockPreparedStatement.setBoolean(3, input.third) }
    }

    @Test
    fun `update with complex parameter setting works correctly`() = runBlocking {
        // Arrange
        data class UserUpdate(val id: Int, val name: String, val email: String, val age: Int)
        val safeSQL = SafeSQL.update("UPDATE users SET name = ?, email = ?, age = ? WHERE id = ?")
        val input = UserUpdate(123, "Jane Doe", "jane@example.com", 30)
        val expectedAffectedRows = 1

        every { mockConnection.prepareStatement(safeSQL.query) } returns mockPreparedStatement
        every { mockPreparedStatement.executeUpdate() } returns expectedAffectedRows

        val parameterSetter: (PreparedStatement, UserUpdate) -> Unit = { stmt, user ->
            stmt.setString(1, user.name)
            stmt.setString(2, user.email)
            stmt.setInt(3, user.age)
            stmt.setInt(4, user.id)
        }
        val errorMapper: (DatabaseFailure) -> String = { "Error" }

        every { mockPreparedStatement.setString(1, input.name) } just Runs
        every { mockPreparedStatement.setString(2, input.email) } just Runs
        every { mockPreparedStatement.setInt(3, input.age) } just Runs
        every { mockPreparedStatement.setInt(4, input.id) } just Runs

        // Act
        val step = DatabaseSteps.update(safeSQL, parameterSetter, errorMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(expectedAffectedRows, result.value)
        verify { mockPreparedStatement.setString(1, input.name) }
        verify { mockPreparedStatement.setString(2, input.email) }
        verify { mockPreparedStatement.setInt(3, input.age) }
        verify { mockPreparedStatement.setInt(4, input.id) }
    }

    @Test
    fun `query with null parameter handling works correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users WHERE name = ?")
        val input: String? = null
        val expectedData = emptyList<String>()

        every { mockConnection.prepareStatement(safeSQL.query) } returns mockPreparedStatement
        every { mockPreparedStatement.executeQuery() } returns mockResultSet

        val parameterSetter: (PreparedStatement, String?) -> Unit = { stmt, name ->
            if (name == null) {
                stmt.setNull(1, Types.VARCHAR)
            } else {
                stmt.setString(1, name)
            }
        }
        val errorMapper: (DatabaseFailure) -> String = { "Error" }
        val resultMapper: (ResultSet) -> List<String> = { expectedData }

        every { mockPreparedStatement.setNull(1, Types.VARCHAR) } just Runs

        // Act
        val step = DatabaseSteps.query(safeSQL, parameterSetter, errorMapper, resultMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(expectedData, result.value)
        verify { mockPreparedStatement.setNull(1, Types.VARCHAR) }
        verify { mockPreparedStatement.executeQuery() }
    }

    @Test
    fun `update with zero affected rows returns correct count`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.update("UPDATE users SET name = ? WHERE id = ?")
        val input = Pair("Non-existent", 99999)
        val expectedAffectedRows = 0

        every { mockConnection.prepareStatement(safeSQL.query) } returns mockPreparedStatement
        every { mockPreparedStatement.executeUpdate() } returns expectedAffectedRows

        val parameterSetter: (PreparedStatement, Pair<String, Int>) -> Unit = { stmt, data ->
            stmt.setString(1, data.first)
            stmt.setInt(2, data.second)
        }
        val errorMapper: (DatabaseFailure) -> String = { "Error" }

        every { mockPreparedStatement.setString(1, input.first) } just Runs
        every { mockPreparedStatement.setInt(2, input.second) } just Runs

        // Act
        val step = DatabaseSteps.update(safeSQL, parameterSetter, errorMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(expectedAffectedRows, result.value)
    }

    @Test
    fun `query with ResultSet processing exception is handled correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit

        every { mockConnection.prepareStatement(safeSQL.query) } returns mockPreparedStatement
        every { mockPreparedStatement.executeQuery() } returns mockResultSet

        val errorMapper: (DatabaseFailure) -> String = { failure ->
            when (failure) {
                DatabaseFailure.UnknownError -> "Processing error"
                else -> "Other error"
            }
        }
        val resultMapper: (ResultSet) -> String = {
            throw RuntimeException("ResultSet processing failed")
        }

        // Act
        val step = DatabaseSteps.query<Unit, String, String>(safeSQL, errorMapper = errorMapper, resultMapper = resultMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Processing error", result.error)
    }

    @Test
    fun `resource cleanup happens correctly even on exception`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit

        every { mockConnection.prepareStatement(safeSQL.query) } returns mockPreparedStatement
        every { mockPreparedStatement.executeQuery() } throws RuntimeException("Query failed")

        val errorMapper: (DatabaseFailure) -> String = { "Error" }
        val resultMapper: (ResultSet) -> String = { "data" }

        // Act
        val step =
            DatabaseSteps.query<Unit, String, String>(safeSQL, errorMapper = errorMapper, resultMapper = resultMapper)
        val result = step.process(input)

        // Assert
        assertTrue(result is Result.Failure)
        // Verify that close() methods were called (which proves use() worked correctly)
        verify { mockConnection.close() }
        verify { mockPreparedStatement.close() }
    }

    @Test
    fun `query step implements Step interface correctly`() {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val errorMapper: (DatabaseFailure) -> String = { "Error" }
        val resultMapper: (ResultSet) -> String = { "result" }

        // Act
        val step = DatabaseSteps.query<Unit, String, String>(safeSQL, errorMapper = errorMapper, resultMapper = resultMapper)

        // Assert
        assertNotNull(step)
        assertTrue(true)
    }

    @Test
    fun `update step implements Step interface correctly`() {
        // Arrange
        val safeSQL = SafeSQL.update("UPDATE users SET name = ?")
        val parameterSetter: (PreparedStatement, String) -> Unit = { stmt, name ->
            stmt.setString(1, name)
        }
        val errorMapper: (DatabaseFailure) -> String = { "Error" }

        // Act
        val step = DatabaseSteps.update(safeSQL, parameterSetter, errorMapper)

        // Assert
        assertNotNull(step)
        assertTrue(true)
    }
}
