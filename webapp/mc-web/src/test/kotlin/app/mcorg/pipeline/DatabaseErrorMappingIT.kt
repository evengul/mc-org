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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
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
                // DROP first, rather than CREATE IF NOT EXISTS. The container is declared
                // withReuse(true) and cleanDatabase() does not know about this table, so on a
                // second local run the rows from the first survived: every test below then hit a
                // duplicate key (23505) before reaching the constraint it is named for and passed
                // as IntegrityConstraintError for the wrong reason. Green, and meaningless.
                statement.execute("DROP TABLE IF EXISTS mapping_probe")
                statement.execute(
                    """
                    CREATE TABLE mapping_probe (
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
    fun `a deadlock maps to ConnectionError, not UnknownError`() {
        // Two transactions taking the same two rows in opposite orders. PostgreSQL detects the
        // cycle and cancels one with 40P01. Provoked for real rather than asserted from a
        // hand-built SQLException, for the same reason as the rest of this file: the driver
        // decides what is thrown, not us.
        //
        // This is not hypothetical. The webhook outbox claim (V2_59_0) and the ingestion cascade
        // delete are both multi-row writes that can interleave, and before the class-40 rule they
        // reported UnknownError — indistinguishable from a mapper bug, and impossible for a caller
        // to identify as retryable.
        insertOk(30)
        insertOk(31)

        val bothHoldOne = CyclicBarrier(2)
        val mapped = ConcurrentLinkedQueue<AppFailure.DatabaseError>()

        fun contend(first: Int, second: Int) = Thread {
            try {
                Database.getConnection().use { connection ->
                    connection.autoCommit = false
                    lockRow(connection, first)
                    bothHoldOne.await(10, TimeUnit.SECONDS)
                    lockRow(connection, second)
                    connection.commit()
                }
            } catch (e: Exception) {
                mapped.add(mapDatabaseException(e))
            }
        }

        val a = contend(30, 31)
        val b = contend(31, 30)
        a.start(); b.start()
        a.join(30_000); b.join(30_000)

        // Exactly one transaction is chosen as the deadlock victim; the other commits.
        assertEquals(listOf(AppFailure.DatabaseError.ConnectionError), mapped.toList())
    }

    private fun insertOk(id: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert("INSERT INTO mapping_probe (id, required) VALUES (?, 'a')"),
            parameterSetter = { statement, _ -> statement.setInt(1, id) }
        ).process(Unit)
    }

    private fun lockRow(connection: java.sql.Connection, id: Int) {
        connection.prepareStatement("UPDATE mapping_probe SET required = 'locked' WHERE id = ?").use {
            it.setInt(1, id)
            it.executeUpdate()
        }
    }

    @Test
    fun `a cancelled statement is a StatementError, not a hung connection`() = runBlocking {
        // Pins the 57014 -> StatementError mapping, and nothing more.
        //
        // Read the previous version of this comment as a warning. It claimed to be "the behaviour
        // the dataSourceProperties exist for", which is not true and was actively misleading:
        // queryTimeout below is set by this test and issues a client-side CancelRequest, so it
        // produces 57014 whether or not Database.kt's connectionInitSql ever ran. Nothing in this
        // file exercises Hikari at all — DatabaseTestExtension hands out DriverManager connections
        // — so connectTimeout, socketTimeout, tcpKeepAlive, connectionInitSql and the pool sizing
        // have no coverage anywhere in the suite.
        //
        // That gap is not closable here: reproducing the failure that actually matters (Neon's
        // pooler rejecting `options=-c statement_timeout`) needs a PgBouncer in front of the
        // container, which Testcontainers Postgres is not. MCO-379 carries the pool-config test as
        // a sequenced step. Until then the protection against re-introducing that outage is the
        // comment in Database.kt, not this test — so do not read a green suite as covering it.
        val result = DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.select("SELECT 1 FROM pg_sleep(60)"),
            parameterSetter = { statement, _ -> statement.queryTimeout = 1 },
            resultMapper = { rs -> if (rs.next()) rs.getInt(1) else 0 },
        ).process(Unit)

        val failure = assertIs<Result.Failure<AppFailure.DatabaseError>>(result)
        assertEquals(AppFailure.DatabaseError.StatementError, failure.error)
    }
}
