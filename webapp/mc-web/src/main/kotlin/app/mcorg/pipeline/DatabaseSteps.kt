package app.mcorg.pipeline

import app.mcorg.config.Database
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import com.zaxxer.hikari.pool.HikariPool
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

@Suppress("SqlSourceToSinkFlow")
object DatabaseSteps {
    fun <I, S> query(
        sql: SafeSQL,
        parameterSetter: (PreparedStatement, I) -> Unit = { _, _ -> },
        resultMapper: (ResultSet) -> S,
        transactionConnection: TransactionConnection? = null,
    ): Step<I, AppFailure.DatabaseError, S> {
        return object : Step<I, AppFailure.DatabaseError, S> {
            override suspend fun process(input: I): Result<AppFailure.DatabaseError, S> {
                return try {
                    val block = { conn: Connection ->
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
                    if (transactionConnection != null) {
                        block(transactionConnection.connection)
                    } else {
                        Database.getConnection().use { connection ->
                            block(connection)
                        }
                    }
                } catch (e: Exception) {
                    logDatabaseFailure("Could not execute query", e)
                    handleException(e)
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
            override suspend fun process(input: I): Result<AppFailure.DatabaseError, Int> {
                return try {
                    val block = { conn: Connection ->
                        conn.prepareStatement(sql.query).use { statement ->
                            parameterSetter(statement, input)
                            if (sql.query.contains("RETURNING", ignoreCase = false)) {
                                statement.executeQuery().use { resultSet ->
                                    if (resultSet.next()) {
                                        val id = resultSet.getInt(1)
                                        Result.success(id)
                                    } else {
                                        Result.failure(AppFailure.DatabaseError.NoIdReturned)
                                    }
                                }
                            } else {
                                // For non-RETURNING queries, we just return the number of affected rows
                                val affectedRows = statement.executeUpdate()
                                Result.success(affectedRows)
                            }
                        }
                    }
                    if (transactionConnection != null) {
                        block(transactionConnection.connection)
                    } else {
                        Database.getConnection().use { connection ->
                            block(connection)
                        }
                    }
                } catch (e: Exception) {
                    logDatabaseFailure("Could not execute update", e)
                    handleException(e)
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
            override suspend fun process(input: List<I>): Result<AppFailure.DatabaseError, Unit> {
                return try {
                    val block = { conn: Connection ->
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
                    if (transactionConnection != null) {
                        block(transactionConnection.connection)
                    } else {
                        Database.getConnection().use { connection ->
                            block(connection)
                        }
                    }
                } catch (e: Exception) {
                    logDatabaseFailure("Could not execute batch update", e)
                    handleException(e)
                }
            }
        }
    }

    fun <I, S> transaction(
        step: (connection: TransactionConnection) -> Step<I, AppFailure.DatabaseError, S>
    ): Step<I, AppFailure.DatabaseError, S> {
        return object : Step<I, AppFailure.DatabaseError, S> {
            override suspend fun process(input: I): Result<AppFailure.DatabaseError, S> {
                return try {
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
                } catch (e: Exception) {
                    logDatabaseFailure("Could not execute transaction", e)
                    handleException(e)
                }
            }
        }
    }

    private fun handleException(e: Exception): Result<AppFailure.DatabaseError, Nothing> =
        Result.failure(mapDatabaseException(e))
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
        sqlState == "57014" -> AppFailure.DatabaseError.StatementError

        else -> AppFailure.DatabaseError.UnknownError
    }
}

data class TransactionConnection(val connection: Connection) {
    fun use(block: (Connection) -> Unit) {
        block(connection)
    }
}
