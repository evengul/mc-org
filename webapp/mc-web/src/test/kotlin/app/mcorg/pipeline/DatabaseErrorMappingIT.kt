package app.mcorg.pipeline

import app.mcorg.config.Database
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * MCO-347 — the `SQLException` → `DatabaseError` mapping, exercised against a real PostgreSQL.
 *
 * The old mapping branched on `SQLTimeoutException`, `SQLSyntaxErrorException` and
 * `SQLIntegrityConstraintViolationException`. pgjdbc does not use the JDBC4 subclass hierarchy —
 * it raises `PSQLException` for all of them — so those three branches could never fire and every
 * failure except a unique violation collapsed to `UnknownError`. `DatabaseStepsTest` asserted the
 * mapping by *mocking* each unreachable exception, so the suite was green on behaviour that
 * cannot occur.
 *
 * These tests provoke the real errors instead. A mock cannot tell you what your driver throws.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class DatabaseErrorMappingIT {

    /**
     * Raw JDBC because [SafeSQL] deliberately has no DDL entry point — it exists to make the
     * application's data access unambiguous, and a test fixture is not that. The table carries one
     * of each constraint class so every SQLState family below has something real to violate.
     */
    @BeforeAll
    fun createFixtures() {
        Database.getConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS mapping_probe (
                        id INTEGER PRIMARY KEY,
                        parent INTEGER REFERENCES mapping_probe(id),
                        required TEXT NOT NULL,
                        small INTEGER CHECK (small < 10)
                    )
                    """.trimIndent()
                )
            }
        }
    }

    private suspend fun insert(sql: String): AppFailure.DatabaseError? {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(sql),
            parameterSetter = { _, _ -> }
        ).process(Unit)
        return (result as? Result.Failure)?.error
    }

    @Test
    fun `a unique violation maps to IntegrityConstraintError`() = runBlocking {
        insert("INSERT INTO mapping_probe (id, required) VALUES (1, 'a')")

        val error = insert("INSERT INTO mapping_probe (id, required) VALUES (1, 'b')")

        assertEquals(AppFailure.DatabaseError.IntegrityConstraintError, error)
    }

    @Test
    fun `a foreign key violation maps to IntegrityConstraintError`() = runBlocking {
        // SQLState 23503. Previously UnknownError — the mapping only ever special-cased 23505.
        val error = insert("INSERT INTO mapping_probe (id, parent, required) VALUES (2, 9999, 'a')")

        assertEquals(AppFailure.DatabaseError.IntegrityConstraintError, error)
    }

    @Test
    fun `a not-null violation maps to IntegrityConstraintError`() = runBlocking {
        // SQLState 23502. Previously UnknownError.
        val error = insert("INSERT INTO mapping_probe (id, required) VALUES (3, NULL)")

        assertEquals(AppFailure.DatabaseError.IntegrityConstraintError, error)
    }

    @Test
    fun `a check constraint violation maps to IntegrityConstraintError`() = runBlocking {
        // SQLState 23514. Previously UnknownError.
        val error = insert("INSERT INTO mapping_probe (id, required, small) VALUES (4, 'a', 99)")

        assertEquals(AppFailure.DatabaseError.IntegrityConstraintError, error)
    }

    @Test
    fun `a syntax error maps to StatementError`() = runBlocking {
        // SQLState 42601. Previously UnknownError, because pgjdbc never throws
        // SQLSyntaxErrorException.
        val error = insert("INSERT INTO mapping_probe (id, required VALUES (5, 'a')")

        assertEquals(AppFailure.DatabaseError.StatementError, error)
    }

    @Test
    fun `an unknown column maps to StatementError`() = runBlocking {
        // SQLState 42703 — same 42xxx family.
        val error = insert("INSERT INTO mapping_probe (id, no_such_column) VALUES (6, 'a')")

        assertEquals(AppFailure.DatabaseError.StatementError, error)
    }

    @Test
    fun `a statement exceeding statement_timeout is a StatementError, not a hung connection`() = runBlocking {
        // The behaviour the dataSourceProperties exist for: the server cancels the query and the
        // connection goes back to the pool, rather than pgjdbc's default socketTimeout of 0
        // pinning it forever.
        val result = DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.select("SELECT 1 FROM pg_sleep(60)"),
            parameterSetter = { statement, _ -> statement.queryTimeout = 1 },
            resultMapper = { rs -> if (rs.next()) rs.getInt(1) else 0 },
        ).process(Unit)

        val failure = assertIs<Result.Failure<AppFailure.DatabaseError>>(result)
        assertEquals(AppFailure.DatabaseError.StatementError, failure.error)
    }
}
