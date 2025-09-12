package app.mcorg.test.integration

import app.mcorg.config.Database
import app.mcorg.config.DatabaseConnectionProvider
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.sql.Connection
import java.sql.DriverManager

/**
 * Base class for integration tests that need real database connections.
 *
 * This class sets up a test database connection and provides utilities for
 * creating test data and cleaning up after tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseIntegrationTest {

    private lateinit var testConnectionProvider: DatabaseConnectionProvider
    private var testConnection: Connection? = null

    @BeforeAll
    fun setupTestDatabase() {
        // Create test database connection provider
        testConnectionProvider = object : DatabaseConnectionProvider {
            override fun getConnection(): Connection {
                return DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/postgres_test",
                    "postgres",
                    "supersecret"
                )
            }
        }

        // Set the test provider in Database
        Database.setProvider(testConnectionProvider)

        // Get a connection to verify it works
        testConnection = testConnectionProvider.getConnection()

        // Run any initial test data setup
        setupTestData()
    }

    @AfterAll
    fun cleanupTestDatabase() {
        // Clean up test data
        cleanupTestData()

        // Close test connection
        testConnection?.close()
    }

    /**
     * Override this method to set up test data before all tests in the class.
     */
    protected open fun setupTestData() {
        // Default: no setup
    }

    /**
     * Override this method to clean up test data after all tests in the class.
     */
    protected open fun cleanupTestData() {
        // Default: no cleanup
    }

    /**
     * Get a direct connection for test data setup/cleanup operations.
     */
    protected fun getTestConnection(): Connection {
        return testConnection ?: throw IllegalStateException("Test database not initialized")
    }

    /**
     * Execute SQL directly for test setup/cleanup operations.
     */
    protected fun executeTestSQL(sql: String) {
        getTestConnection().createStatement().use { statement ->
            statement.execute(sql)
        }
    }
}
