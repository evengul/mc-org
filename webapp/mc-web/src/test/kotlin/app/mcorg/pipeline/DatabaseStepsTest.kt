package app.mcorg.pipeline

import app.mcorg.config.Database
import app.mcorg.config.DatabaseConnectionProvider
import app.mcorg.pipeline.failure.AppFailure
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

    /*
     * These used to mock SQLTimeoutException, SQLSyntaxErrorException and
     * SQLIntegrityConstraintViolationException — the JDBC4 subclasses pgjdbc never throws
     * (MCO-347). They passed, and proved nothing: the branches they exercised could not fire
     * against a real driver, and every one of these failures actually became UnknownError in
     * production. Mocking a shape your driver cannot produce is a test of the mock.
     *
     * Now they mock what pgjdbc does produce — a SQLException carrying a real SQLState — and
     * DatabaseErrorMappingIT covers the same ground against a live PostgreSQL.
     */

    @Test
    fun `query maps a connection-failure SQLState to ConnectionError`() = runBlocking {
        // Arrange — 08006, connection_failure.
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit

        every { mockConnection.prepareStatement(safeSQL.query) } throws SQLException("gone", "08006")

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
    fun `query maps pool exhaustion to ConnectionError`() = runBlocking {
        // Hikari raises SQLTransientConnectionException when the pool cannot hand over a
        // connection in time. It is a sibling of SQLTimeoutException rather than a subtype, so
        // the old `is SQLTimeoutException` branch missed it — and this is the most likely
        // database failure in production, not an exotic one.
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit

        every { mockConnection.prepareStatement(safeSQL.query) } throws
            SQLTransientConnectionException("Pool - Connection is not available, request timed out")

        val resultMapper: (ResultSet) -> String = { "data" }

        val result = TestUtils.executeAndAssertFailure(
            DatabaseSteps.query(safeSQL, resultMapper = resultMapper),
            input
        )

        assertEquals(AppFailure.DatabaseError.ConnectionError, result)
    }

    @Test
    fun `query maps a syntax-error SQLState to StatementError`() = runBlocking {
        // Arrange — 42601, syntax_error.
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit

        every { mockConnection.prepareStatement(safeSQL.query) } throws SQLException("bad sql", "42601")

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
    fun `query maps a constraint-violation SQLState to IntegrityConstraintError`() = runBlocking {
        // Arrange — 23503, foreign_key_violation. Deliberately not 23505: the old mapping
        // special-cased the unique violation and nothing else in class 23.
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit

        every { mockConnection.prepareStatement(safeSQL.query) } throws SQLException("fk", "23503")

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
    fun `query maps a wrapped SQLException by walking the cause chain`() = runBlocking {
        // Hikari and pgjdbc both wrap in places; a SQLState two levels down still counts.
        val safeSQL = SafeSQL.select("SELECT * FROM users")
        val input = Unit

        every { mockConnection.prepareStatement(safeSQL.query) } throws
            RuntimeException("wrapper", SQLException("fk", "23503"))

        val resultMapper: (ResultSet) -> String = { "data" }

        val result = TestUtils.executeAndAssertFailure(
            DatabaseSteps.query(safeSQL, resultMapper = resultMapper),
            input
        )

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
    fun `update maps a connection-failure SQLState to ConnectionError`() = runBlocking {
        // Arrange — 08006, connection_failure. See the note above the query-side equivalents.
        val safeSQL = SafeSQL.insert("INSERT INTO users (name) VALUES (?)")
        val input = "John Doe"

        every { mockConnection.prepareStatement(safeSQL.query) } throws SQLException("gone", "08006")

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
    fun `update maps a syntax-error SQLState to StatementError`() {
        // Arrange — 42601, syntax_error.
        val safeSQL = SafeSQL.update("UPDATE users SET name = ?")
        val input = "John Doe"

        every { mockConnection.prepareStatement(safeSQL.query) } throws SQLException("bad sql", "42601")

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
    fun `update maps a constraint-violation SQLState to IntegrityConstraintError`() {
        // Arrange — 23502, not_null_violation. Another class-23 state the old mapping missed.
        val safeSQL = SafeSQL.insert("INSERT INTO users (email) VALUES (?)")
        val input = "duplicate@example.com"

        every { mockConnection.prepareStatement(safeSQL.query) } throws SQLException("not null", "23502")

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
    fun `update handles unknown exceptions correctly`(): Unit = runBlocking {
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
