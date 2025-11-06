package app.mcorg.pipeline

import app.mcorg.config.Database
import app.mcorg.config.DatabaseConnectionProvider
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.test.utils.TestUtils
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
        val resultMapper: (ResultSet) -> String = { rs -> expectedData }

        // Act
        val step = DatabaseSteps.query(safeSQL, parameterSetter, resultMapper)
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

        val resultMapper: (ResultSet) -> Int = { expectedCount }

        // Act
        val result = TestUtils.executeAndAssertSuccess(
            DatabaseSteps.query(safeSQL, resultMapper = resultMapper),
            input
        )

        // Assert
        assertEquals(expectedCount, result)
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

        val resultMapper: (ResultSet) -> String = { "data" }

        // Act
        val result = TestUtils.executeAndAssertFailure(
            DatabaseSteps.query(safeSQL, resultMapper = resultMapper),
            input
        )

        // Assert
        assertEquals(AppFailure.DatabaseError.ConnectionError, result)
    }

    @Test
    fun `query handles SQLSyntaxErrorException correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit
        val syntaxException = SQLSyntaxErrorException("Invalid SQL syntax")

        every { mockConnection.prepareStatement(safeSQL.query) } throws syntaxException

        val resultMapper: (ResultSet) -> String = { "data" }

        // Act
        val result = TestUtils.executeAndAssertFailure(
            DatabaseSteps.query(safeSQL, resultMapper = resultMapper),
            input
        )

        // Assert
        assertEquals(AppFailure.DatabaseError.StatementError, result)
    }

    @Test
    fun `query handles SQLIntegrityConstraintViolationException correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit
        val constraintException = SQLIntegrityConstraintViolationException("Constraint violation")

        every { mockConnection.prepareStatement(safeSQL.query) } throws constraintException

        val resultMapper: (ResultSet) -> String = { "data" }

        // Act
        val result = TestUtils.executeAndAssertFailure(
            DatabaseSteps.query(safeSQL, resultMapper = resultMapper),
            input
        )

        // Assert
        assertEquals(AppFailure.DatabaseError.IntegrityConstraintError, result)
    }

    @Test
    fun `query handles unknown exceptions correctly`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit
        val unknownException = RuntimeException("Unknown error")

        every { mockConnection.prepareStatement(safeSQL.query) } throws unknownException

        val resultMapper: (ResultSet) -> String = { "data" }

        // Act
        val result = TestUtils.executeAndAssertFailure(
            DatabaseSteps.query(safeSQL, resultMapper = resultMapper),
            input
        )

        // Assert
        assertEquals(AppFailure.DatabaseError.UnknownError, result)
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

        every { mockPreparedStatement.setString(1, input.first) } just Runs
        every { mockPreparedStatement.setInt(2, input.second) } just Runs

        // Act
        val result = TestUtils.executeAndAssertSuccess(
            DatabaseSteps.update(safeSQL, parameterSetter),
            input
        )

        // Assert
        assertEquals(expectedAffectedRows, result)
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

        // Act
        val result = TestUtils.executeAndAssertFailure(
            DatabaseSteps.update(safeSQL, parameterSetter),
            input
        )

        // Assert
        assertEquals(AppFailure.DatabaseError.ConnectionError, result)
    }

    @Test
    fun `update handles SQLSyntaxErrorException correctly`() {
        // Arrange
        val safeSQL = SafeSQL.update("UPDATE users SET name = ?")
        val input = "John Doe"
        val syntaxException = SQLSyntaxErrorException("Invalid SQL syntax")

        every { mockConnection.prepareStatement(safeSQL.query) } throws syntaxException

        val parameterSetter: (PreparedStatement, String) -> Unit = { stmt, name ->
            stmt.setString(1, name)
        }

        // Act
        val result = TestUtils.executeAndAssertFailure(
            DatabaseSteps.update(safeSQL, parameterSetter),
            input
        )

        // Assert
        assertIs<AppFailure.DatabaseError.StatementError>(result)
    }

    @Test
    fun `update handles SQLIntegrityConstraintViolationException correctly`() {
        // Arrange
        val safeSQL = SafeSQL.insert("INSERT INTO users (email) VALUES (?)")
        val input = "duplicate@example.com"
        val constraintException = SQLIntegrityConstraintViolationException("Unique constraint violation")

        every { mockConnection.prepareStatement(safeSQL.query) } throws constraintException

        val parameterSetter: (PreparedStatement, String) -> Unit = { stmt, email ->
            stmt.setString(1, email)
        }

        // Act
        val result = TestUtils.executeAndAssertFailure(
            DatabaseSteps.update(safeSQL, parameterSetter),
            input
        )

        // Assert
        assertIs<AppFailure.DatabaseError.IntegrityConstraintError>(result)
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

        // Act
        val result = TestUtils.executeAndAssertFailure(
            DatabaseSteps.update(safeSQL, parameterSetter),
            input
        )

        // Assert
        assertIs<AppFailure.DatabaseError.UnknownError>(result)
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
        val resultMapper: (ResultSet) -> List<String> = { expectedResult }

        every { mockPreparedStatement.setInt(1, input.first) } just Runs
        every { mockPreparedStatement.setString(2, input.second) } just Runs
        every { mockPreparedStatement.setBoolean(3, input.third) } just Runs

        // Act
        val result = TestUtils.executeAndAssertSuccess(
            DatabaseSteps.query(safeSQL, parameterSetter, resultMapper),
            input
        )

        // Assert
        assertEquals(expectedResult, result)
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

        every { mockPreparedStatement.setString(1, input.name) } just Runs
        every { mockPreparedStatement.setString(2, input.email) } just Runs
        every { mockPreparedStatement.setInt(3, input.age) } just Runs
        every { mockPreparedStatement.setInt(4, input.id) } just Runs

        // Act
        val result = TestUtils.executeAndAssertSuccess(
            DatabaseSteps.update(safeSQL, parameterSetter, ),
            input
        )

        // Assert
        assertEquals(expectedAffectedRows, result)
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
        val resultMapper: (ResultSet) -> List<String> = { expectedData }

        every { mockPreparedStatement.setNull(1, Types.VARCHAR) } just Runs

        // Act
        val result = TestUtils.executeAndAssertSuccess(
            DatabaseSteps.query(safeSQL, parameterSetter, resultMapper),
            input
        )

        // Assert
        assertEquals(expectedData, result)
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

        every { mockPreparedStatement.setString(1, input.first) } just Runs
        every { mockPreparedStatement.setInt(2, input.second) } just Runs

        // Act
        val result = TestUtils.executeAndAssertSuccess(
            DatabaseSteps.update(safeSQL, parameterSetter),
            input
        )

        // Assert
        assertEquals(expectedAffectedRows, result)
    }

    @Test
    fun `query with ResultSet processing exception is handled correctly`() {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit

        every { mockConnection.prepareStatement(safeSQL.query) } returns mockPreparedStatement
        every { mockPreparedStatement.executeQuery() } returns mockResultSet

        val resultMapper: (ResultSet) -> String = {
            throw SQLException("ResultSet processing failed")
        }

        // Act
        val result = TestUtils.executeAndAssertFailure(
            DatabaseSteps.query(safeSQL, resultMapper = resultMapper),
            input
        )

        // Assert
        assertIs<AppFailure.DatabaseError.ResultMappingError>(result)
    }

    @Test
    fun `resource cleanup happens correctly even on exception`() = runBlocking {
        // Arrange
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit

        every { mockConnection.prepareStatement(safeSQL.query) } returns mockPreparedStatement
        every { mockPreparedStatement.executeQuery() } throws RuntimeException("Query failed")

        val resultMapper: (ResultSet) -> String = { "data" }

        // Act
        TestUtils.executeAndAssertFailure(
            DatabaseSteps.query(safeSQL, resultMapper = resultMapper),
            input
        )

        // Assert
        verify { mockConnection.close() }
        verify { mockPreparedStatement.close() }
    }
}
