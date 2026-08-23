package app.mcorg.config

import app.mcorg.domain.Local
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.sql.Connection

/*
 * Database timeouts, in seconds (MCO-347).
 *
 * Sized around the slowest thing that legitimately runs on this pool, which is not a web request
 * but the nightly ingestion: its heaviest statement is the cascading
 * `DELETE FROM resource_source WHERE version = ?`. Measured on a production fork at **171ms**
 * for the largest version (3,269 parent rows) with V2_58_0's foreign-key indexes in place, so 30s
 * is ~175x headroom.
 *
 * Two corrections to what this comment used to say, because the numbers matter if you ever retune
 * it: the statement deletes at most ~3.3k rows, not 76k — 76k is the whole-table count and the
 * `WHERE version = ?` predicate never touches it — and the old "~3s" was extrapolated from a
 * 200-row sample rather than measured.
 *
 * The headroom is entirely conditional on those indexes. Without them the same statement takes
 * 30.29s — 0.29s *over* this timeout. If V2_58_0 is ever rolled back or renumbered out, the
 * nightly re-ingest starts failing with 57014 every night, and the failure will look like a
 * timeout bug rather than a missing index. Raise this constant or restore the indexes; do not
 * treat the two as independent.
 *
 * The ordering between the last two is load-bearing. STATEMENT_TIMEOUT must stay comfortably below
 * SOCKET_TIMEOUT so the *server* cancels the query and returns a 57014 the pool can recover from;
 * if the socket gave up first we would be back to discarding connections, just on a timer. Raise
 * one and you must raise the other.
 */
private const val CONNECT_TIMEOUT_SECONDS = "10"
private const val STATEMENT_TIMEOUT_SECONDS = "30"
private const val SOCKET_TIMEOUT_SECONDS = "60"

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

            // Socket timeouts (MCO-347).
            //
            // Hikari's connectionTimeout above bounds only how long we wait for a connection from
            // the *pool*. Nothing bounded the conversation with PostgreSQL once we had one:
            // pgjdbc defaults socketTimeout to 0, meaning infinite, so a runaway query pinned a
            // pooled connection permanently and ten of them exhausted production with no route
            // back to health short of a restart.
            //
            // These three are handled entirely inside pgjdbc and never sent to the server, which
            // is what makes them safe through Neon's pooler — see the statement_timeout note
            // below for why that distinction cost a production outage.
            //
            // What socketTimeout recovers is the *pool slot*, not the server. When it fires pgjdbc
            // closes the client socket without sending a CancelRequest, so the backend keeps
            // executing the runaway query to completion. That is why statement_timeout below
            // exists as well: it is the only one of the two that stops the server doing work.
            addDataSourceProperty("connectTimeout", CONNECT_TIMEOUT_SECONDS)
            addDataSourceProperty("socketTimeout", SOCKET_TIMEOUT_SECONDS)
            addDataSourceProperty("tcpKeepAlive", "true")

            // Server-side statement timeout, set per connection rather than as a startup option.
            //
            // The obvious spelling — `options=-c statement_timeout=30s` in dataSourceProperties —
            // does not merely fail to apply through Neon's pooler, it makes the pooler *reject
            // the connection*: "unsupported startup parameter in options: statement_timeout".
            // Since fly.toml points at the -pooler endpoint, shipping that would have failed
            // every connection in production. Verified against the real endpoint.
            //
            // `ALTER DATABASE ... SET statement_timeout` is no good either: it takes on the direct
            // endpoint but the pooler reports 0 regardless, so the database default never reaches
            // a pooled backend.
            //
            // What does work is a plain SET on each physical connection — verified to persist
            // across later statements on the same pooled session. Best-effort rather than
            // guaranteed, because a transaction-pooled backend can in principle be swapped
            // underneath us (the same session-state question as MCO-348); socketTimeout above is
            // the backstop that does not depend on it.
            //
            // This leaks outward, which is worth knowing before anyone reuses the trick. Neon's
            // pooler reuses one server backend across unrelated client connections and does not
            // issue DISCARD ALL, so this SET is inherited by whatever connects next as the same
            // user on the *pooled* endpoint — verified: five fresh psql sessions all reported this
            // value on the same backend pid. Production is unaffected where it matters, because
            // Flyway and the ingest-scheduler both run on the *direct* endpoint (the production
            // DB_URL variable has no -pooler; only fly.toml's app env does), so no migration can
            // be cancelled by a timeout the app set. What does inherit it is
            // `ingest-machine.sh --once`, which goes through the pooler. Harmless at 30s today.
            // The robust fix, if this ever needs to be exact, is per-statement (SET LOCAL inside
            // the transaction, or setQueryTimeout) since only that is transaction-scoped.
            connectionInitSql = "SET statement_timeout = '${STATEMENT_TIMEOUT_SECONDS}s'"

            // Connection testing. Left as an explicit query rather than Hikari's preferred
            // isValid() so validation re-runs through the same path the application uses.
            // Note Hikari applies validationTimeout below (5s) to this query as both query and
            // network timeout, so it is bounded tighter than socketTimeout, not by it.
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