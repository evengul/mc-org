package app.mcorg.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.DriverManager

object Database {
    @Volatile
    private var dataSource: HikariDataSource? = null

    fun getConnection(): Connection {
        return if (isLocalEnvironment()) {
            getLocalConnection()
        } else {
            getProductionConnection()
        }
    }

    private fun isLocalEnvironment(): Boolean = System.getenv("ENV") == "LOCAL"

    private fun getLocalConnection(): Connection {
        if (dataSource == null) {
            synchronized(this) {
                if (dataSource == null) {
                    val config = Config.get()
                    dataSource = HikariDataSource(HikariConfig().apply {
                        jdbcUrl = config.url
                        username = config.user
                        password = config.password
                        driverClassName = "org.postgresql.Driver"
                        maximumPoolSize = 5
                        minimumIdle = 1
                    })
                }
            }
        }
        return dataSource!!.connection
    }

    private fun getProductionConnection(): Connection {
        val config = Config.get()
        return DriverManager.getConnection(config.url, config.user, config.password)
    }

    private data class Config(val url: String, val user: String, val password: String) {
        companion object {
            fun get(): Config {
                return Config(System.getenv("DB_URL"), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"))
            }
        }
    }
}