package app.mcorg.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection

interface DatabaseConnectionProvider {
    fun getConnection(): Connection
}

class HikariDatabaseProvider(isProduction: Boolean) : DatabaseConnectionProvider {
    @Volatile
    private var dataSource: HikariDataSource? = null

    private val poolConfig = if (isProduction) {
        PoolConfig(
            maximumPoolSize = 10,
            minimumIdle = 2,
            connectionTimeout = 30000,
            idleTimeout = 600000,
            maxLifetime = 1800000
        )
    } else {
        PoolConfig(
            maximumPoolSize = 5,
            minimumIdle = 1,
            connectionTimeout = 30000,
            idleTimeout = 600000,
            maxLifetime = 1800000
        )
    }

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
                        maximumPoolSize = poolConfig.maximumPoolSize
                        minimumIdle = poolConfig.minimumIdle
                        connectionTimeout = poolConfig.connectionTimeout
                        idleTimeout = poolConfig.idleTimeout
                        maxLifetime = poolConfig.maxLifetime
                        leakDetectionThreshold = 60000 // 60 seconds
                    })
                }
            }
        }
        return dataSource!!.connection
    }

    private data class PoolConfig(
        val maximumPoolSize: Int,
        val minimumIdle: Int,
        val connectionTimeout: Long,
        val idleTimeout: Long,
        val maxLifetime: Long
    )
}

object Database {
    private var provider: DatabaseConnectionProvider? = null

    fun getConnection(): Connection {
        return getProvider().getConnection()
    }

    private fun getProvider(): DatabaseConnectionProvider {
        if (provider == null) {
            provider = HikariDatabaseProvider(isProduction = !isLocalEnvironment())
        }
        return provider!!
    }

    private fun isLocalEnvironment(): Boolean = AppConfig.dbUrl.contains("localhost")

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