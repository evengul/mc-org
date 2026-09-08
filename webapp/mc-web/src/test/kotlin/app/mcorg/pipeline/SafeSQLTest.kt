package app.mcorg.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * `SafeSQL` checks *shape*: the factory's statement kind, and one statement per instance. It does
 * not — cannot — check for interpolation; that is `SafeSqlSourceScanTest`'s job (MCO-551).
 */
class SafeSQLTest {

    private val factories = mapOf(
        "select" to SafeSQL::select,
        "with" to SafeSQL::with,
        "insert" to SafeSQL::insert,
        "update" to SafeSQL::update,
        "delete" to SafeSQL::delete,
    )

    private fun build(query: String): SafeSQL {
        val kind = factories.keys.first { query.trim().lowercase().startsWith(it) }
        return factories.getValue(kind)(query)
    }

    @Test
    fun `each factory accepts its own statement kind, whitespace and case included`() {
        val accepted = listOf(
            "SELECT * FROM users",
            "select id, name from products",
            "   SELECT * FROM table   ",
            "\nSELECT * FROM users\n",
            "WITH ranked AS (SELECT 1) SELECT * FROM ranked",
            "INSERT INTO users (name, email) VALUES (?, ?)",
            "INSERT INTO orders SELECT * FROM temp_orders",
            "\t\tINSERT INTO users VALUES (1, 'test')\t\t",
            "UPDATE users SET name = ? WHERE id = ?",
            "Update users set name = 'test'",
            "DELETE FROM users WHERE id = ?",
            "Delete from users where id = 1",
        )
        accepted.forEach { query ->
            val safeSQL = build(query)
            assertNotNull(safeSQL)
            assertEquals(query, safeSQL.query)
        }
    }

    @Test
    fun `a factory refuses another kind of statement, naming the kind it wanted`() {
        val expectations = listOf(
            Triple(SafeSQL::select, "SELECT", "Query must be a SELECT statement"),
            Triple(SafeSQL::with, "WITH", "Query must be a WITH statement"),
            Triple(SafeSQL::insert, "INSERT", "Query must be an INSERT statement"),
            Triple(SafeSQL::update, "UPDATE", "Query must be an UPDATE statement"),
            Triple(SafeSQL::delete, "DELETE", "Query must be a DELETE statement"),
        )
        val everyKind = listOf(
            "SELECT * FROM users",
            "WITH x AS (SELECT 1) SELECT * FROM x",
            "INSERT INTO users VALUES (1, 'test')",
            "UPDATE users SET name = 'test'",
            "DELETE FROM users",
            "CREATE TABLE test (id INT)",
            "",
        )
        expectations.forEach { (factory, ownKeyword, message) ->
            everyKind.filterNot { it.trim().uppercase().startsWith(ownKeyword) }.forEach { query ->
                val exception = assertThrows<IllegalArgumentException> { factory(query) }
                assertEquals(message, exception.message, "for query '$query'")
            }
        }
    }

    @Test
    fun `a trailing semicolon, or one inside a quoted literal or a line comment, is allowed`() {
        listOf(
            "SELECT * FROM users;",
            "INSERT INTO users VALUES (1, 'test');",
            "UPDATE users SET name = 'test';",
            "DELETE FROM users WHERE id = 1;",
            "SELECT * FROM users;   ",
            "UPDATE d SET last_error = COALESCE(last_error, 'Abandoned in flight; claim expired')",
            "SELECT 'it''s; quoted' FROM t;",
            "SELECT id\n  -- Base list only; build-time modes carry theirs alongside\nFROM ideas",
        ).forEach { query ->
            assertEquals(query, build(query).query)
        }
    }

    @Test
    fun `a second statement after a semicolon is refused`() {
        listOf(
            "SELECT * FROM users; DROP TABLE users;",
            "SELECT * FROM users; DROP TABLE users",
            "SELECT * FROM users;; SELECT * FROM passwords;",
            "INSERT INTO users VALUES (1, 'test'); DELETE FROM users;",
            "SELECT * FROM users; EXEC xp_cmdshell 'dir'",
        ).forEach { query ->
            val exception = assertThrows<IllegalArgumentException> { build(query) }
            assertEquals("SafeSQL holds a single statement; a ';' may only end it", exception.message, "for query '$query'")
        }
    }

    @Test
    fun `words that a denylist would have flagged are none of SafeSQL's business`() {
        // Until MCO-551 a substring denylist (`exec`, `sp_`, `xp_`, whole-word DDL keywords) rejected
        // some of these. It guarded nothing — the text is a constant either way — and would have
        // refused a legitimate column such as executed_at.
        listOf(
            "SELECT created_at, executed_at FROM users WHERE updated_at > ?",
            "INSERT INTO logs (created_at, dropped_items) VALUES (?, ?)",
            "UPDATE users SET created_at = NOW() WHERE altered_by = ?",
            "DELETE FROM audit WHERE created_at < ? AND truncated_data IS NULL",
            "SELECT regexp_replace(name, 'x', 'y') FROM crisp_things",
            "SELECT * FROM users WHERE role = 'create'",
        ).forEach { query ->
            assertEquals(query, build(query).query)
        }
    }

    @Test
    fun `value class behaviour - equals, hashCode and the original text`() {
        val query = "SELECT id, name FROM users WHERE active = true"
        val one = SafeSQL.select(query)
        val two = SafeSQL.select(query)
        assertEquals(query, one.query)
        assertEquals(one, two)
        assertEquals(one.hashCode(), two.hashCode())
        assertEquals(one.toString(), two.toString())
    }
}
