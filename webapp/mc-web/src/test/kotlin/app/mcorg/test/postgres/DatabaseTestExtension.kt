package app.mcorg.test.postgres

import app.mcorg.config.CacheManager
import app.mcorg.config.Database
import app.mcorg.config.DatabaseConnectionProvider
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager

/**
 * JUnit 5 extension for managing PostgreSQL Testcontainer lifecycle during testing.
 * Provides a clean PostgreSQL 18 database with Flyway migrations for each test class.
 */
class DatabaseTestExtension : BeforeAllCallback {

    companion object {
        private val postgres: PostgreSQLContainer by lazy {
            PostgreSQLContainer(DockerImageName.parse("postgres:18"))
                .withDatabaseName("mcorg_test")
                .withUsername("test_user")
                .withPassword("test_password")
                .withReuse(true) // Reuse container across test runs for speed
        }

        /**
         * Get JDBC URL for the test database
         */
        fun getJdbcUrl(): String = postgres.jdbcUrl

        /**
         * Get database username
         */
        fun getUsername(): String = postgres.username

        /**
         * Get database password
         */
        fun getPassword(): String = postgres.password

        /**
         * Execute a SQL statement directly against the test database
         */
        fun executeSQL(sql: String) {
            DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword()).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(sql)
                }
            }
        }

        /**
         * Clean all data from tables while preserving schema structure
         */
        fun cleanDatabase() {
            executeSQL("""
                TRUNCATE TABLE 
                    world_members,
                    projects,
                    project_dependencies,
                    invites,
                    notifications,
                    world
                RESTART IDENTITY CASCADE
            """)
        }

        /**
         * Empties the Minecraft game-data tables — the state a fresh container starts with.
         *
         * Every test class shares one container, and twenty of them store their own versions,
         * items and recipes. Left in place, one class's data turns into another's recipe graph:
         * `GatheringPlannerIT` expects no graph for its world's version and got iron ingots
         * resolved through someone else's recipes, and `ItemSourceGraphStepsTest` left a ledger
         * row stamped in the future that made every later lookup of 1.21.4 rebuild instead of
         * hitting the cache. Which class ran first decided the outcome, so CI's class order went
         * red while the local one stayed green (MCO-570).
         *
         * The cluster only references itself, so the cascade stops here; the two migrations that
         * insert into it are backfills that copy nothing on an empty database.
         */
        fun resetMinecraftData() {
            executeSQL(
                """
                TRUNCATE TABLE
                    minecraft_version_ingestion,
                    resource_source_consumed_item,
                    resource_source_consumed_tag,
                    resource_source_produced_item,
                    resource_source_produced_tag,
                    resource_source,
                    minecraft_tag_item,
                    minecraft_tag,
                    minecraft_items,
                    minecraft_version
                RESTART IDENTITY CASCADE
                """
            )
        }

        /**
         * Points [Database] back at the container.
         *
         * Extracted from [beforeAll] so a test that deliberately swaps in a failing provider can
         * put the real one back afterwards (MCO-349's readiness-probe cases). Without a way to
         * restore, a broken provider would leak into every later class in the reused surefire JVM.
         */
        fun installProvider() {
            Database.setProvider(object : DatabaseConnectionProvider {
                override fun getConnection(): Connection {
                    return DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword())
                }

                override fun close() {
                    postgres.close()
                }
            })
        }
    }

    override fun beforeAll(context: ExtensionContext) {
        // Start PostgreSQL container if not already running
        if (!postgres.isRunning) {
            postgres.start()
        }

        // Run Flyway migrations to set up schema
        val flyway = Flyway.configure()
            .dataSource(getJdbcUrl(), getUsername(), getPassword())
            .locations("classpath:db/migration")
            .load()

        flyway.migrate()

        installProvider()

        // Each class starts from no game data and empty caches — see [resetMinecraftData].
        // The caches go with it: a graph or role cached by the previous class outlives its rows.
        resetMinecraftData()
        CacheManager.invalidateAll()
    }
}
