package app.mcorg.config

import app.mcorg.domain.Local
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.sql.Connection

interface DatabaseConnectionProvider : AutoCloseable {
    fun getConnection(): Connection
}

class HikariDatabaseProvider(private val isProduction: Boolean) : DatabaseConnectionProvider {
    private val logger = LoggerFactory.getLogger(HikariDatabaseProvider::class.java)

    @Volatile
    private var dataSource: HikariDataSource? = null

    private val poolConfig = if (isProduction) {
        PoolConfig(
            maximumPoolSize = 10,
            minimumIdle = 0,
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
                    initializeDataSource()
                }
            }
        }
        return dataSource!!.connection
    }

    private fun initializeDataSource() {
        val config = Database.Config.get()
        // Name the profile *and* the env: the old message said "for PRODUCTION environment" on
        // every non-localhost run, which read as "you are connected to production" (MCO-335).
        logger.info(
            "Initializing HikariCP connection pool with the {} profile (ENV={})",
            if (isProduction) "shared" else "local",
            AppConfig.env,
        )

        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            driverClassName = "org.postgresql.Driver"

            // Pool sizing
            maximumPoolSize = poolConfig.maximumPoolSize
            minimumIdle = poolConfig.minimumIdle

            // Timeouts
            connectionTimeout = poolConfig.connectionTimeout
            idleTimeout = poolConfig.idleTimeout
            maxLifetime = poolConfig.maxLifetime

            // Connection testing
            connectionTestQuery = "SELECT 1"
            validationTimeout = 5000

            // Leak detection (only in non-production for debugging)
            if (!isProduction) {
                leakDetectionThreshold = 60000 // 60 seconds
            }

            // Pool name for monitoring
            poolName = if (isProduction) "MCOrg-Shared-Pool" else "MCOrg-Local-Pool"

            // Additional performance settings
            isAutoCommit = true
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
        })

        logger.info("HikariCP pool initialized: maxPoolSize={}, minIdle={}",
            poolConfig.maximumPoolSize, poolConfig.minimumIdle)
    }

    override fun close() {
        dataSource?.let {
            if (!it.isClosed) {
                logger.info("Closing HikariCP connection pool")
                it.close()
            }
        }
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
    private val logger = LoggerFactory.getLogger(Database::class.java)
    private var provider: DatabaseConnectionProvider? = null

    init {
        // Register shutdown hook to close the pool gracefully
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown()
        })
    }

    fun getConnection(): Connection {
        return getProvider().getConnection()
    }

    private fun getProvider(): DatabaseConnectionProvider {
        if (provider == null) {
            synchronized(this) {
                if (provider == null) {
                    provider = HikariDatabaseProvider(isProduction = !isLocalEnvironment())
                }
            }
        }
        return provider!!
    }

    // Keyed off ENV, not the JDBC host (MCO-335). The old `dbUrl.contains("localhost")` test meant
    // every worktree — Neon host, ENV=LOCAL — silently got the PRODUCTION profile, disabling leak
    // detection exactly where you develop.
    private fun isLocalEnvironment(): Boolean = AppConfig.env == Local

    // Gracefully shutdown the connection pool
    fun shutdown() {
        provider?.let {
            try {
                logger.info("Shutting down database connection pool")
                it.close()
                provider = null
            } catch (e: Exception) {
                logger.error("Error closing database connection pool", e)
            }
        }
    }

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