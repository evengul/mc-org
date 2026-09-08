package app.mcorg.config

import app.mcorg.test.postgres.DatabaseTestExtension
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `SchemaValidation` against a real, migrated PostgreSQL (MCO-551). The two failure cases put
 * `flyway_schema_history` back exactly as they found it: the extension reuses one database across
 * every IT class and re-runs `migrate` before each, which would fail on a history row left missing.
 */
@Tag("database")
@ExtendWith(DatabaseTestExtension::class)
class SchemaValidationIT {

    private fun problems(): List<String> = SchemaValidation.problems(
        DatabaseTestExtension.getJdbcUrl(),
        DatabaseTestExtension.getUsername(),
        DatabaseTestExtension.getPassword(),
    )

    private fun connect(): Connection = DriverManager.getConnection(
        DatabaseTestExtension.getJdbcUrl(),
        DatabaseTestExtension.getUsername(),
        DatabaseTestExtension.getPassword(),
    )

    @Test
    fun `a migrated database has no problems`() {
        assertEquals(emptyList(), problems())
    }

    @Test
    fun `a database missing the newest migration names it`() {
        connect().use { conn ->
            conn.createStatement().use {
                it.execute(
                    "CREATE TEMP TABLE kept AS SELECT * FROM flyway_schema_history " +
                        "WHERE installed_rank = (SELECT max(installed_rank) FROM flyway_schema_history)"
                )
            }
            val version = conn.createStatement().use { st ->
                st.executeQuery("SELECT version FROM kept").use { rs -> rs.next(); rs.getString(1) }
            }
            try {
                conn.createStatement().use {
                    it.execute("DELETE FROM flyway_schema_history WHERE installed_rank = (SELECT installed_rank FROM kept)")
                }
                val problems = problems()
                assertTrue(problems.isNotEmpty(), "expected the missing migration to be reported")
                assertTrue(problems.any { version in it }, "expected version $version to be named, got $problems")
            } finally {
                conn.createStatement().use { it.execute("INSERT INTO flyway_schema_history SELECT * FROM kept") }
            }
        }
    }

    @Test
    fun `an edited migration is a checksum problem`() {
        connect().use { conn ->
            try {
                conn.createStatement().use {
                    it.execute(
                        "UPDATE flyway_schema_history SET checksum = checksum + 1 " +
                            "WHERE installed_rank = (SELECT max(installed_rank) FROM flyway_schema_history)"
                    )
                }
                val problems = problems()
                assertTrue(problems.any { "checksum" in it.lowercase() }, "expected a checksum problem, got $problems")
            } finally {
                conn.createStatement().use {
                    it.execute(
                        "UPDATE flyway_schema_history SET checksum = checksum - 1 " +
                            "WHERE installed_rank = (SELECT max(installed_rank) FROM flyway_schema_history)"
                    )
                }
            }
        }
    }
}
