package app.mcorg.pipeline

import app.mcorg.config.Database
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import com.zaxxer.hikari.pool.HikariPool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory
import java.sql.*

private val logger = LoggerFactory.getLogger("DatabaseSteps")

/**
 * Logs a database failure without letting the driver's message into the log (MCO-339).
 *
 * PostgreSQL puts the offending *values* in the error: a unique violation carries a
 * `DETAIL: Key (email)=(someone@example.com) already exists` line, and `PSQLException.getMessage()`
 * concatenates that detail onto the primary message. For `api_token.token_hash` the same line is a
 * token hash. Passing the exception to the logger leaks it exactly as thoroughly as interpolating
 * the message, because the rendered stack trace begins with `getMessage()`.
 *
 * What survives is what you actually debug from — SQLState, constraint, table, routine — which is
 * enough to identify *which* constraint failed without recording *which row* failed it.
 *
 * Non-SQL exceptions (a mapper NPE, say) keep their stack trace: it carries code locations, not row
 * data. The cause chain is checked too, so a wrapper around a SQLException does not sneak through.
 */
private fun logDatabaseFailure(what: String, e: Exception) {
    val sqlCause = generateSequence(e as Throwable?) { it.cause }.filterIsInstance<SQLException>().firstOrNull()
    if (sqlCause == null) {
        logger.error(what, e)
        return
    }
    val server = (sqlCause as? PSQLException)?.serverErrorMessage
    val description = buildString {
        append(sqlCause.javaClass.simpleName)
        append(" sqlState=").append(sqlCause.sqlState ?: "unknown")
        server?.constraint?.let { append(" constraint=").append(it) }
        server?.table?.let { append(" table=").append(it) }
        server?.routine?.let { append(" routine=").append(it) }
    }
    logger.error("{}: {} (message withheld — it can carry row values)", what, description)
}

/**
 * Runs one JDBC interaction off the caller's thread (MCO-551).
 *
 * JDBC blocks, and Ktor's Netty engine runs the application call on its call event-loop group,
 * which defaults to `availableProcessors()` threads — **one** on production's 1-vCPU Fly machine:
 * every request line in the production log carries `[eventLoopGroupProxy-4-1]`. Until this
 * helper existed every statement ran on that thread for its whole duration, so one slow query
 * stalled every other request, static assets included. Measured locally with the JVM pinned to a
 * single processor: a favicon that serves in 39ms took 9.4s while one `/worlds` request sat
 * behind a table lock.
 *
 * `Dispatchers.IO` is the documented home for blocking calls. Its thread count (64) exceeding the
 * pool's ten connections is deliberate: a burst beyond the pool now queues on Hikari's
 * `connectionTimeout` on an IO thread, which is backpressure, rather than on the event loop,
 * which is an outage.
 *
 * Cancellation is rethrown, not mapped. A cancelled request is not a database failure; turning it
 * into one hid the cancellation and let the pipeline carry on after its caller was gone (the
 * same shape as the webhook-poller bug fixed under MCO-326).
 */
private suspend fun <S> jdbc(
    what: String,
    transactionConnection: TransactionConnection?,
    block: (Connection) -> Result<AppFailure.DatabaseError, S>,
): Result<AppFailure.DatabaseError, S> = withContext(Dispatchers.IO) {
    try {
        if (transactionConnection != null) {
            block(transactionConnection.connection)
        } else {
            Database.getConnection().use { block(it) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logDatabaseFailure(what, e)
        Result.failure(mapDatabaseException(e))
    }
}

@Suppress("SqlSourceToSinkFlow")
object DatabaseSteps {
    fun <I, S> query(
        sql: SafeSQL,
        parameterSetter: (PreparedStatement, I) -> Unit = { _, _ -> },
        resultMapper: (ResultSet) -> S,
        transactionConnection: TransactionConnection? = null,
    ): Step<I, AppFailure.DatabaseError, S> {
        return object : Step<I, AppFailure.DatabaseError, S> {
            override suspend fun process(input: I): Result<AppFailure.DatabaseError, S> =
                jdbc("Could not execute query", transactionConnection) { conn ->
                    conn.prepareStatement(sql.query).use { statement ->
                        parameterSetter(statement, input)
                        statement.executeQuery().use { resultSet ->
                            try {
                                Result.success(resultMapper(resultSet))
                            } catch (e: SQLException) {
                                logDatabaseFailure("Error mapping result set", e)
                                Result.failure(AppFailure.DatabaseError.ResultMappingError)
                            }
                        }
                    }
                }
        }
    }

    fun <I> update(
        sql: SafeSQL,
        parameterSetter: (PreparedStatement, I) -> Unit,
        transactionConnection: TransactionConnection? = null,
    ): Step<I, AppFailure.DatabaseError, Int> {
        return object : Step<I, AppFailure.DatabaseError, Int> {
            override suspend fun process(input: I): Result<AppFailure.DatabaseError, Int> =
                jdbc("Could not execute update", transactionConnection) { conn ->
                    conn.prepareStatement(sql.query).use { statement ->
                        parameterSetter(statement, input)
                        if (sql.query.contains("RETURNING", ignoreCase = false)) {
                            statement.executeQuery().use { resultSet ->
                                if (resultSet.next()) {
                                    Result.success(resultSet.getInt(1))
                                } else {
                                    Result.failure(AppFailure.DatabaseError.NoIdReturned)
                                }
                            }
                        } else {
                            // For non-RETURNING queries, we just return the number of affected rows
                            Result.success(statement.executeUpdate())
                        }
                    }
                }
        }
    }

    fun <I> batchUpdate(
        sql: SafeSQL,
        parameterSetter: (PreparedStatement, I) -> Unit,
        chunkSize: Int = 500,
        transactionConnection: TransactionConnection? = null,
    ): Step<List<I>, AppFailure.DatabaseError, Unit> {
        return object : Step<List<I>, AppFailure.DatabaseError, Unit> {
            override suspend fun process(input: List<I>): Result<AppFailure.DatabaseError, Unit> =
                jdbc("Could not execute batch update", transactionConnection) { conn ->
                    conn.prepareStatement(sql.query).use { statement ->
                        input.chunked(chunkSize).forEach { chunk ->
                            statement.clearBatch()
                            chunk.forEach { item ->
                                parameterSetter(statement, item)
                                statement.addBatch()
                            }
                            val results = statement.executeBatch()
                            val successCount = results.count { it > 0 }
                            if (successCount != chunk.size) {
                                logger.warn("Expected to affect ${chunk.size} rows, but only $successCount were affected.")
                            }
                        }
                        Result.success<AppFailure.DatabaseError>()
                    }
                }
        }
    }

    /**
     * Runs [step] inside one transaction. The inner step must thread the [TransactionConnection]
     * into every `DatabaseSteps` call it makes: a call without it opens its own pooled connection
     * and silently runs *outside* the transaction. As of MCO-551 all sixteen inner calls in the
     * tree pass it — keep it that way.
     */
    fun <I, S> transaction(
        step: (connection: TransactionConnection) -> Step<I, AppFailure.DatabaseError, S>
    ): Step<I, AppFailure.DatabaseError, S> {
        return object : Step<I, AppFailure.DatabaseError, S> {
            override suspend fun process(input: I): Result<AppFailure.DatabaseError, S> = withContext(Dispatchers.IO) {
                try {
                    Database.getConnection().use { connection ->
                        connection.autoCommit = false
                        try {
                            when (val result = step(TransactionConnection(connection)).process(input)) {
                                is Result.Success -> {
                                    connection.commit()
                                    result
                                }
                                is Result.Failure -> {
                                    connection.rollback()
                                    result
                                }
                            }
                        } catch (e: Exception) {
                            connection.rollback()
                            throw e
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logDatabaseFailure("Could not execute transaction", e)
                    Result.failure(mapDatabaseException(e))
                }
            }
        }
    }
}

/**
 * Maps a driver exception to a [AppFailure.DatabaseError] by **SQLState**, not by exception class
 * (MCO-347).
 *
 * The previous mapping branched on `SQLTimeoutException`, `SQLSyntaxErrorException` and
 * `SQLIntegrityConstraintViolationException`. pgjdbc does not use the JDBC4 subclass hierarchy —
 * it raises a plain `PSQLException` for all of them — so those three branches were unreachable and
 * everything except a unique violation became `UnknownError`. Verified against a real PostgreSQL
 * in `DatabaseErrorMappingIT`, which failed 6 of 7 before this change.
 *
 * SQLState is the right key precisely because it is the driver-independent part: the codes are
 * specified by SQL and by PostgreSQL's documented error table, and they do not depend on which
 * exception class a driver author happened to pick.
 */
internal fun mapDatabaseException(e: Throwable): AppFailure.DatabaseError {
    // Hikari raises SQLTransientConnectionException when the pool times out handing over a
    // connection. It is a *sibling* of SQLTimeoutException, not a subtype, so the old
    // `is SQLTimeoutException` branch never caught it — meaning pool exhaustion, the single most
    // likely production database failure, reported as UnknownError.
    val chain = generateSequence(e) { it.cause }
    if (chain.any { it is SQLTransientConnectionException || it is HikariPool.PoolInitializationException }) {
        return AppFailure.DatabaseError.ConnectionError
    }

    val sqlState = chain.filterIsInstance<SQLException>().firstOrNull()?.sqlState
        ?: return AppFailure.DatabaseError.UnknownError

    return when {
        // Class 23 — integrity constraint violation: unique (23505), foreign key (23503),
        // not null (23502), check (23514), exclusion (23P01).
        sqlState.startsWith("23") -> AppFailure.DatabaseError.IntegrityConstraintError

        // Class 42 — syntax error or access rule violation: malformed SQL, unknown column or
        // table, wrong argument types. Always a bug in our SQL rather than in the data.
        sqlState.startsWith("42") -> AppFailure.DatabaseError.StatementError

        // Class 08 — connection exception, and class 53 — insufficient server resources
        // (out of memory, too many connections, disk full). Both mean "not this connection's
        // fault, and retrying later may work".
        sqlState.startsWith("08") -> AppFailure.DatabaseError.ConnectionError
        sqlState.startsWith("53") -> AppFailure.DatabaseError.ConnectionError

        // 57014 — query cancelled, which is what both statement_timeout and JDBC queryTimeout
        // produce. Reported as a statement problem because that is what it is: this query was too
        // slow. The connection itself is healthy and goes straight back to the pool, which is the
        // entire point of setting those timeouts.
        //
        // Kept ahead of the class-57 rule below, which would otherwise swallow it.
        sqlState == "57014" -> AppFailure.DatabaseError.StatementError

        // Class 40 — serialization failure (40001) and deadlock detected (40P01). These are the
        // canonical *retryable* errors: nothing is wrong with the statement or the connection, two
        // transactions simply collided and one was chosen as the victim. Not hypothetical here —
        // the webhook outbox claim (V2_59_0) and the ingestion cascade delete are both shapes that
        // deadlock under concurrency, and before this they reported UnknownError, indistinguishable
        // from a mapper bug.
        //
        // Mapped to ConnectionError rather than a variant of their own because that is the closest
        // existing "transient, try again later" signal. A dedicated TransientError would be more
        // honest; it is not worth widening the sealed hierarchy in this branch.
        sqlState.startsWith("40") -> AppFailure.DatabaseError.ConnectionError

        // Rest of class 57 — operator intervention: admin_shutdown (57P01), cannot_connect_now
        // (57P03), crash_shutdown (57P02). This is exactly what a Neon compute emits when it
        // suspends or restarts a backend, which with autosuspend at 300s is a routine event rather
        // than an incident. The connection is gone; a new one will work.
        sqlState.startsWith("57") -> AppFailure.DatabaseError.ConnectionError

        else -> AppFailure.DatabaseError.UnknownError
    }
}

data class TransactionConnection(val connection: Connection)
