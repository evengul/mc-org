package app.mcorg.infrastructure.repository

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.DriverManager

private var dataSource: HikariDataSource? = null

open class Repository {
    fun getConnection(): Connection {
        val env = System.getenv("ENV")

        if (env == "LOCAL") {
            if (dataSource == null) {
                dataSource = HikariDataSource(HikariConfig().apply {
                    val config = Config.get()
                    jdbcUrl = config.url
                    username = config.password
                    password = config.password
                    driverClassName = "org.postgresql.Driver"
                })
            }
            return dataSource!!.connection
        }

        val (url, username, password) = Config.get()
        return DriverManager.getConnection(url, username, password)
    }
}

data class Config(val url: String, val user: String, val password: String) {
    companion object {
        fun get(): Config {
            return Config(System.getenv("DB_URL"), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))
        }
    }
}