package app.mcorg.pipeline

import app.mcorg.domain.pipeline.Result
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.SQLSyntaxErrorException
import java.sql.SQLTimeoutException

sealed interface DatabaseFailure {
    data object ConnectionError : DatabaseFailure
    data object StatementError : DatabaseFailure
    data object IntegrityConstraintError : DatabaseFailure
    data object UnknownError : DatabaseFailure
    data object NotFound : DatabaseFailure
}

fun <S> useConnection(handler: Connection.() -> Result<DatabaseFailure, S>): Result<DatabaseFailure, S> {
    val logger = LoggerFactory.getLogger("DatabaseStep")

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
                val config = app.mcorg.infrastructure.repository.Config.get()
                jdbcUrl = config.url
                username = config.user
                password = config.password
                driverClassName = "org.postgresql.Driver"
            })
        }
        return dataSource!!.connection
    }

    val (url, username, password) = app.mcorg.infrastructure.repository.Config.get()
    return DriverManager.getConnection(url, username, password)
}

data class Config(val url: String, val user: String, val password: String) {
    companion object {
        fun get(): Config {
            return Config(System.getenv("DB_URL"), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))
        }
    }
}