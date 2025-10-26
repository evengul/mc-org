package app.mcorg.pipeline

import app.mcorg.config.Database
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.DatabaseFailure
import org.slf4j.LoggerFactory
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.SQLSyntaxErrorException
import java.sql.SQLTimeoutException

private val logger = LoggerFactory.getLogger("DatabaseSteps")

@Suppress("SqlSourceToSinkFlow")
object DatabaseSteps {
    fun <I, E, S> query(
        sql: SafeSQL,
        parameterSetter: (PreparedStatement, I) -> Unit = { _, _ -> },
        errorMapper: (DatabaseFailure) -> E,
        resultMapper: (ResultSet) -> S
    ): Step<I, E, S> {
        return object : Step<I, E, S> {
            override suspend fun process(input: I): Result<E, S> {
                return try {
                    Database.getConnection().use { connection ->
                        connection.prepareStatement(sql.query).use { statement ->
                            parameterSetter(statement, input)
                            statement.executeQuery().use { resultSet ->
                                Result.success(resultMapper(resultSet))
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Could not execute query", e)
                    val failure = when (e) {
                        is SQLTimeoutException -> DatabaseFailure.ConnectionError
                        is SQLSyntaxErrorException -> DatabaseFailure.StatementError
                        is SQLIntegrityConstraintViolationException -> DatabaseFailure.IntegrityConstraintError
                        else -> DatabaseFailure.UnknownError
                    }
                    Result.failure(errorMapper(failure))
                }
            }
        }
    }

    fun <I, E> update(
        sql: SafeSQL,
        parameterSetter: (PreparedStatement, I) -> Unit,
        errorMapper: (DatabaseFailure) -> E
    ): Step<I, E, Int> {
        return object : Step<I, E, Int> {
            override suspend fun process(input: I): Result<E, Int> {
                return try {
                    Database.getConnection().use { connection ->
                        connection.prepareStatement(sql.query).use { statement ->
                            parameterSetter(statement, input)
                            if (sql.query.contains("RETURNING", ignoreCase = false)) {
                                statement.executeQuery().use { resultSet ->
                                    if (resultSet.next()) {
                                        val id = resultSet.getInt(1)
                                        Result.success(id)
                                    } else {
                                        Result.failure(errorMapper(DatabaseFailure.UnknownError))
                                    }
                                }
                            } else {
                                // For non-RETURNING queries, we just return the number of affected rows
                                val affectedRows = statement.executeUpdate()
                                Result.success(affectedRows)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Could not execute update", e)
                    val failure = when (e) {
                        is SQLTimeoutException -> DatabaseFailure.ConnectionError
                        is SQLSyntaxErrorException -> DatabaseFailure.StatementError
                        is SQLIntegrityConstraintViolationException -> DatabaseFailure.IntegrityConstraintError
                        else -> DatabaseFailure.UnknownError
                    }
                    Result.failure(errorMapper(failure))
                }
            }
        }
    }

    fun <I, E> batchUpdate(
        sql: SafeSQL,
        parameterSetter: (PreparedStatement, I) -> Unit,
        errorMapper: (DatabaseFailure) -> E,
        chunkSize: Int = 500
    ): Step<List<I>, E, Unit> {
        return object : Step<List<I>, E, Unit> {
            override suspend fun process(input: List<I>): Result<E, Unit> {
                return try {
                    Database.getConnection().use { connection ->
                        connection.prepareStatement(sql.query).use { statement ->
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
                            Result.success()
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Could not execute batch update", e)
                    val failure = when (e) {
                        is SQLTimeoutException -> DatabaseFailure.ConnectionError
                        is SQLSyntaxErrorException -> DatabaseFailure.StatementError
                        is SQLIntegrityConstraintViolationException -> DatabaseFailure.IntegrityConstraintError
                        else -> DatabaseFailure.UnknownError
                    }
                    Result.failure(errorMapper(failure))
                }
            }
        }
    }

    fun <I, E, S> transaction(
        step: Step<I, E, S>,
        errorMapper: (DatabaseFailure) -> E
    ): Step<I, E, S> {
        return object : Step<I, E, S> {
            override suspend fun process(input: I): Result<E, S> {
                return try {
                    Database.getConnection().use { connection ->
                        connection.autoCommit = false
                        try {
                            val result = step.process(input)
                            when (result) {
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
                    logger.error("Could not execute transaction", e)
                    val failure = when (e) {
                        is SQLTimeoutException -> DatabaseFailure.ConnectionError
                        is SQLSyntaxErrorException -> DatabaseFailure.StatementError
                        is SQLIntegrityConstraintViolationException -> DatabaseFailure.IntegrityConstraintError
                        else -> DatabaseFailure.UnknownError
                    }
                    Result.failure(errorMapper(failure))
                }
            }
        }
    }
}
