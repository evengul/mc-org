package app.mcorg.config

import app.mcorg.domain.Local
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.DriverManager

interface DatabaseConnectionProvider {
    fun getConnection(): Connection
}

class ProductionDatabaseProvider : DatabaseConnectionProvider {
    override fun getConnection(): Connection {
        val config = Database.Config.get()
        return DriverManager.getConnection(config.url, config.user, config.password)
    }
}

class LocalDatabaseProvider : DatabaseConnectionProvider {
    @Volatile
    private var dataSource: HikariDataSource? = null

    override fun getConnection(): Connection {
        if (dataSource == null) {
            synchronized(this) {
                if (dataSource == null) {
                    val config = Database.Config.get()
                    dataSource = HikariDataSource(HikariConfig().apply {
                        jdbcUrl = config.url
                        username = config.user
                        password = config.password
                        driverClassName = "org.postgresql.Driver"
                        maximumPoolSize = 5
                        minimumIdle = 1
                        connectionTimeout = 5000
                    })
                }
            }
        }
        return dataSource!!.connection
    }
}

object Database {
    private var provider: DatabaseConnectionProvider? = null

    fun getConnection(): Connection {
        return getProvider().getConnection()
    }

    private fun getProvider(): DatabaseConnectionProvider {
        if (provider == null) {
            provider = if (isLocalEnvironment()) {
                LocalDatabaseProvider()
            } else {
                ProductionDatabaseProvider()
            }
        }
        return provider!!
    }

    private fun isLocalEnvironment(): Boolean = AppConfig.env == Local

    // For testing purposes
    internal fun setProvider(testProvider: DatabaseConnectionProvider) {
        provider = testProvider
    }

    // For testing purposes - reset to default behavior
    internal fun resetProvider() {
        provider = null
    }

    data class Config(val url: String, val user: String, val password: String) {
        companion object {
            fun get(): Config {
                return Config(AppConfig.dbUrl, AppConfig.dbUsername, AppConfig.dbPassword)
            }
        }
    }
}