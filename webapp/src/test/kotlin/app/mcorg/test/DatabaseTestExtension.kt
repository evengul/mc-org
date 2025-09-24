package app.mcorg.test

import app.mcorg.config.Database
import app.mcorg.config.DatabaseConnectionProvider
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager

/**
 * JUnit 5 extension for managing PostgreSQL Testcontainer lifecycle during testing.
 * Provides a clean PostgreSQL 16.9 database with Flyway migrations for each test class.
 */
class DatabaseTestExtension : BeforeAllCallback {

    companion object {
        private val postgres: PostgreSQLContainer<*> by lazy {
            PostgreSQLContainer(DockerImageName.parse("postgres:16.9"))
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
                    tasks,
                    project_dependencies,
                    invites,
                    notifications,
                    world
                RESTART IDENTITY CASCADE
            """)
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

        Database.setProvider(object : DatabaseConnectionProvider {
            override fun getConnection(): Connection {
                return DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword())
            }
        })
    }
}
