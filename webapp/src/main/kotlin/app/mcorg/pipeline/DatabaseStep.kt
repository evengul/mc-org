package app.mcorg.pipeline

import app.mcorg.config.Database
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.DatabaseFailure
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.SQLSyntaxErrorException
import java.sql.SQLTimeoutException

private val logger = LoggerFactory.getLogger("DatabaseStep")

fun <E>PreparedStatement.getReturnedId(error: E): Result<E, Int> {
    if (execute()) {
        with (resultSet) {
            if (next()) {
                return Result.success(getInt(1))
            }
        }
    }
    logger.error("Database statement did not return an ID")
    return Result.failure(error)
}

fun <E, S> useConnection(
    databaseFailureMapper: (DatabaseFailure) -> E,
    handler: Connection.() -> Result<E, S>
): Result<E, S> {
    return try {
        Database.getConnection().use { it.handler() }
    } catch (e: Exception) {
        logger.error("Could not execute statement", e)
        val failure = when (e) {
            is SQLTimeoutException -> DatabaseFailure.ConnectionError
            is SQLSyntaxErrorException -> DatabaseFailure.StatementError
            is SQLIntegrityConstraintViolationException -> DatabaseFailure.IntegrityConstraintError
            else -> DatabaseFailure.UnknownError
        }
        Result.failure(databaseFailureMapper(failure))
    }
}

fun <S> useConnection(handler: Connection.() -> Result<DatabaseFailure, S>): Result<DatabaseFailure, S> {

    return try {
        Database.getConnection().use { it.handler() }
    } catch (e: Exception) {
        logger.error("Could not execute statement", e)
        when (e) {
            is SQLTimeoutException -> Result.failure(DatabaseFailure.ConnectionError)
            is SQLSyntaxErrorException -> Result.failure(DatabaseFailure.StatementError)
            is SQLIntegrityConstraintViolationException -> Result.failure(DatabaseFailure.IntegrityConstraintError)
            else -> Result.failure(DatabaseFailure.UnknownError)
        }
    }
}