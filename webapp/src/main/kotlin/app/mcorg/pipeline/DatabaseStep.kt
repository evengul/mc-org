package app.mcorg.pipeline

import app.mcorg.domain.pipeline.Result
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.SQLSyntaxErrorException
import java.sql.SQLTimeoutException

sealed interface DatabaseFailure {
    data object ConnectionError : DatabaseFailure
    data object StatementError : DatabaseFailure
    data object IntegrityConstraintError : DatabaseFailure
    data object UnknownError : DatabaseFailure
    data object NoIdReturned : DatabaseFailure
    data object NotFound : DatabaseFailure
}

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
        getConnection().use { it.handler() }
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
        getConnection().use { it.handler() }
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

private var dataSource: HikariDataSource? = null

private fun getConnection(): Connection {
    val env = System.getenv("ENV")

    if (env == "LOCAL") {
        if (dataSource == null) {
            dataSource = HikariDataSource(HikariConfig().apply {
                val config = Config.get()
                jdbcUrl = config.url
                username = config.user
                password = config.password
                driverClassName = "org.postgresql.Driver"
            })
        }
        return dataSource!!.connection
    }

    val (url, username, password) = Config.get()
    return DriverManager.getConnection(url, username, password)
}

data class Config(val url: String, val user: String, val password: String) {
    companion object {
        fun get(): Config {
            return Config(System.getenv("DB_URL"), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))
        }
    }
}