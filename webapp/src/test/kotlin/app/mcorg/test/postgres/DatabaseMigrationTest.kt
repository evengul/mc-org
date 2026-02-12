package app.mcorg.test.postgres

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.sql.DriverManager

/**
 * Test to ensure database migrations run smoothly and create expected schema structure.
 * Uses the DatabaseTestExtension to provide a clean PostgreSQL environment.
 */
@ExtendWith(DatabaseTestExtension::class)
class DatabaseMigrationTest {

    @Test
    fun `should successfully apply all Flyway migrations`() {
        // The DatabaseTestExtension already runs migrations in beforeAll()
        // This test verifies that the migration process completed without errors
        // by checking that we can connect and query basic schema information

        val connection = DriverManager.getConnection(
            DatabaseTestExtension.getJdbcUrl(),
            DatabaseTestExtension.getUsername(),
            DatabaseTestExtension.getPassword()
        )

        connection.use { conn ->
            val metaData = conn.metaData
            val tables = metaData.getTables(null, null, "flyway_schema_history", null)
            assertTrue(tables.next(), "Flyway schema history table should exist")

            val expectedTables = listOf(
                "world",
                "users",
                "world_members",
                "projects",
                "invites",
                "notifications",
                "project_dependencies",
                "global_user_roles"
            )

            expectedTables.forEach { tableName ->
                val tableResult = metaData.getTables(null, null, tableName, null)
                assertTrue(tableResult.next(), "Table '$tableName' should exist after migrations")
            }

            // Verify we can execute a basic query on each core table
            val statement = conn.createStatement()
            expectedTables.forEach { tableName ->
                assertDoesNotThrow {
                    statement.executeQuery("SELECT COUNT(*) FROM $tableName").use { rs ->
                        assertTrue(rs.next())
                        val count = rs.getInt(1)
                        assertTrue(count >= 0, "Count should be non-negative for table '$tableName'")
                    }
                }
            }
        }
    }

    @Test
    fun `should have correct foreign key relationships`() {
        val connection = DriverManager.getConnection(
            DatabaseTestExtension.getJdbcUrl(),
            DatabaseTestExtension.getUsername(),
            DatabaseTestExtension.getPassword()
        )

        connection.use { conn ->
            val metaData = conn.metaData

            // Check some critical foreign key relationships exist
            val foreignKeys = metaData.getImportedKeys(null, null, "world_members")
            var foundWorldFK = false
            var foundUserFK = false

            while (foreignKeys.next()) {
                val pkTableName = foreignKeys.getString("PKTABLE_NAME")
                when (pkTableName) {
                    "world" -> foundWorldFK = true
                    "users" -> foundUserFK = true
                }
            }

            assertTrue(foundWorldFK, "world_members should have foreign key to world table")
            assertTrue(foundUserFK, "world_members should have foreign key to users table")
        }
    }

    @Test
    fun `Should be able to use DatabaseSteps for queries`() = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert("INSERT INTO world (name, version, created_at) VALUES ('Test World', '1.20.4', NOW())"),
            parameterSetter = { _, _ -> }
        ).process(Unit)
        val result = DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.select("SELECT COUNT(*) as count FROM world WHERE name = 'Test World'"),
            resultMapper = { rs -> if (rs.next()) rs.getInt("count") else 0 }
        ).process(Unit)

        assertTrue(result is Result.Success)
        assertEquals(1, result.getOrNull()!!)
    }
}
