package app.mcorg.pipeline

import app.mcorg.config.Database
import app.mcorg.config.DatabaseConnectionProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins the thread contract of `DatabaseSteps` (MCO-551): the JDBC work runs on `Dispatchers.IO`,
 * never on the thread that called `process`. The caller here is a dedicated single-thread
 * dispatcher standing in for Netty's call event loop, which on production's 1-vCPU machine is
 * one thread — the one every request shares.
 */
class DatabaseStepsDispatcherTest {

    private val provider = mockk<DatabaseConnectionProvider>()
    private val connection = mockk<Connection>(relaxed = true)
    private val statement = mockk<PreparedStatement>(relaxed = true)
    private val resultSet = mockk<ResultSet>(relaxed = true)

    private val callThread = Executors.newSingleThreadExecutor { Thread(it, "call-thread") }
    private val callDispatcher = callThread.asCoroutineDispatcher()

    @BeforeEach
    fun setup() {
        Database.setProvider(provider)
        every { provider.getConnection() } returns connection
        every { connection.prepareStatement(any()) } returns statement
        every { statement.executeQuery() } returns resultSet
        every { statement.executeUpdate() } returns 1
    }

    @AfterEach
    fun teardown() {
        Database.resetProvider()
        unmockkAll()
        callThread.shutdownNow()
    }

    // Prefix checks: the coroutine debug agent, on in tests, appends " @coroutine#N" to thread names.
    private fun assertOffCallThread(thread: String?) {
        assertTrue(thread != null && !thread.startsWith("call-thread"), "JDBC work ran on the calling thread ($thread)")
        assertTrue(thread.startsWith("DefaultDispatcher-worker"), "expected a Dispatchers.IO thread, got $thread")
    }

    @Test
    fun `query maps the result set off the calling thread`() = runBlocking(callDispatcher) {
        assertTrue(Thread.currentThread().name.startsWith("call-thread"))
        var jdbcThread: String? = null
        val result = DatabaseSteps.query<Unit, Unit>(
            sql = SafeSQL.select("SELECT 1"),
            resultMapper = { jdbcThread = Thread.currentThread().name },
        ).process(Unit)
        assertIs<Result.Success<Unit>>(result)
        assertOffCallThread(jdbcThread)
    }

    @Test
    fun `update binds its parameters off the calling thread`() = runBlocking(callDispatcher) {
        var jdbcThread: String? = null
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE t SET a = 1"),
            parameterSetter = { _, _ -> jdbcThread = Thread.currentThread().name },
        ).process(Unit)
        assertIs<Result.Success<Int>>(result)
        assertOffCallThread(jdbcThread)
    }

    @Test
    fun `batchUpdate binds its parameters off the calling thread`() = runBlocking(callDispatcher) {
        every { statement.executeBatch() } returns intArrayOf(1)
        var jdbcThread: String? = null
        val result = DatabaseSteps.batchUpdate<Int>(
            sql = SafeSQL.insert("INSERT INTO t (a) VALUES (?)"),
            parameterSetter = { _, _ -> jdbcThread = Thread.currentThread().name },
        ).process(listOf(1))
        assertIs<Result.Success<Unit>>(result)
        assertOffCallThread(jdbcThread)
    }

    @Test
    fun `transaction runs its inner step off the calling thread and commits`() = runBlocking(callDispatcher) {
        var innerThread: String? = null
        val result = DatabaseSteps.transaction<Unit, String> { tx ->
            DatabaseSteps.query(
                sql = SafeSQL.select("SELECT 1"),
                resultMapper = { innerThread = Thread.currentThread().name; "done" },
                transactionConnection = tx,
            )
        }.process(Unit)
        assertEquals("done", result.getOrNull())
        assertOffCallThread(innerThread)
        io.mockk.verify { connection.commit() }
    }

    @Test
    fun `cancellation inside a step is rethrown, not reported as a database error`() = runBlocking {
        assertFailsWith<CancellationException> {
            DatabaseSteps.query<Unit, Unit>(
                sql = SafeSQL.select("SELECT 1"),
                resultMapper = { throw CancellationException("caller went away") },
            ).process(Unit)
        }
        Unit
    }
}
