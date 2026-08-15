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

    private fun handleException(e: Exception): Result<AppFailure.DatabaseError, Nothing> {
        return Result.failure(
            when (e) {
                is SQLTimeoutException -> AppFailure.DatabaseError.ConnectionError
                is SQLSyntaxErrorException -> AppFailure.DatabaseError.StatementError
                is SQLIntegrityConstraintViolationException -> AppFailure.DatabaseError.IntegrityConstraintError
                is HikariPool.PoolInitializationException -> AppFailure.DatabaseError.ConnectionError
                is PSQLException if e.sqlState == "23505" -> AppFailure.DatabaseError.IntegrityConstraintError
                else -> AppFailure.DatabaseError.UnknownError
            }
        )
    }
}

data class TransactionConnection(val connection: Connection) {
    fun use(block: (Connection) -> Unit) {
        block(connection)
    }
}
