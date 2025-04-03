package app.mcorg.pipeline

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.DriverManager

fun <I, E, S> Step<I, E, S>.useConnection(handler: Connection.() -> Result<E, S>): Result<E, S> {
    return getConnection().use { it.handler() }
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