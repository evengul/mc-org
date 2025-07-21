package app.mcorg.pipeline

import app.mcorg.pipeline.v2.SafeSQL
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SafeSQLTest {

    @Test
    fun `select creates valid SafeSQL for SELECT statements`() {
        val validSelectQueries = listOf(
            "SELECT * FROM users",
            "select id, name from products",
            "SELECT COUNT(*) FROM orders WHERE status = ?",
            "   SELECT * FROM table   ", // with whitespace
            "SELECT DISTINCT category FROM items"
        )

        validSelectQueries.forEach { query ->
            val safeSQL = SafeSQL.select(query)
            assertNotNull(safeSQL)
            assertEquals(query, safeSQL.query)
        }
    }

    @Test
    fun `select throws exception for non-SELECT statements`() {
        val invalidQueries = listOf(
            "INSERT INTO users VALUES (1, 'test')",
            "UPDATE users SET name = 'test'",
            "DELETE FROM users",
            "CREATE TABLE test (id INT)",
            "DROP TABLE users",
            ""
        )

        invalidQueries.forEach { query ->
            val exception = assertThrows<IllegalArgumentException> {
                SafeSQL.select(query)
            }
            assertEquals("Query must be a SELECT statement", exception.message)
        }
    }

    @Test
    fun `insert creates valid SafeSQL for INSERT statements`() {
        val validInsertQueries = listOf(
            "INSERT INTO users (name, email) VALUES (?, ?)",
            "insert into products values (1, 'test')",
            "INSERT INTO orders SELECT * FROM temp_orders",
            "   INSERT INTO table VALUES (1)   " // with whitespace
        )

        validInsertQueries.forEach { query ->
            val safeSQL = SafeSQL.insert(query)
            assertNotNull(safeSQL)
            assertEquals(query, safeSQL.query)
        }
    }

    @Test
    fun `insert throws exception for non-INSERT statements`() {
        val invalidQueries = listOf(
            "SELECT * FROM users",
            "UPDATE users SET name = 'test'",
            "DELETE FROM users",
            "CREATE TABLE test (id INT)",
            ""
        )

        invalidQueries.forEach { query ->
            val exception = assertThrows<IllegalArgumentException> {
                SafeSQL.insert(query)
            }
            assertEquals("Query must be an INSERT statement", exception.message)
        }
    }

    @Test
    fun `update creates valid SafeSQL for UPDATE statements`() {
        val validUpdateQueries = listOf(
            "UPDATE users SET name = ? WHERE id = ?",
            "update products set price = 100",
            "UPDATE orders SET status = 'shipped' WHERE id IN (1, 2, 3)",
            "   UPDATE table SET col = 'value'   " // with whitespace
        )

        validUpdateQueries.forEach { query ->
            val safeSQL = SafeSQL.update(query)
            assertNotNull(safeSQL)
            assertEquals(query, safeSQL.query)
        }
    }

    @Test
    fun `update throws exception for non-UPDATE statements`() {
        val invalidQueries = listOf(
            "SELECT * FROM users",
            "INSERT INTO users VALUES (1, 'test')",
            "DELETE FROM users",
            "CREATE TABLE test (id INT)",
            ""
        )

        invalidQueries.forEach { query ->
            val exception = assertThrows<IllegalArgumentException> {
                SafeSQL.update(query)
            }
            assertEquals("Query must be an UPDATE statement", exception.message)
        }
    }

    @Test
    fun `delete creates valid SafeSQL for DELETE statements`() {
        val validDeleteQueries = listOf(
            "DELETE FROM users WHERE id = ?",
            "DELETE FROM products WHERE price < 10",
            "DELETE FROM orders WHERE created_at < ?",
            "   DELETE FROM table WHERE condition   " // with whitespace
        )

        validDeleteQueries.forEach { query ->
            val safeSQL = SafeSQL.delete(query)
            assertNotNull(safeSQL)
            assertEquals(query, safeSQL.query)
        }
    }

    @Test
    fun `delete throws exception for non-DELETE statements`() {
        val invalidQueries = listOf(
            "SELECT * FROM users",
            "INSERT INTO users VALUES (1, 'test')",
            "UPDATE users SET name = 'test'",
            "CREATE TABLE test (id INT)",
            ""
        )

        invalidQueries.forEach { query ->
            val exception = assertThrows<IllegalArgumentException> {
                SafeSQL.delete(query)
            }
            assertEquals("Query must be a DELETE statement", exception.message)
        }
    }

    @Test
    fun `dangerous patterns are rejected - semicolon injection`() {
        val dangerousQueries = listOf(
            "SELECT * FROM users; DROP TABLE users;",
            "SELECT * FROM users;; SELECT * FROM passwords;",
            "INSERT INTO users VALUES (1, 'test'); DELETE FROM users;"
        )

        dangerousQueries.forEach { query ->
            val exception = assertThrows<IllegalArgumentException> {
                when {
                    query.lowercase().startsWith("select") -> SafeSQL.select(query)
                    query.lowercase().startsWith("insert") -> SafeSQL.insert(query)
                    else -> SafeSQL.select(query)
                }
            }
            assertEquals("SQL query contains potentially unsafe patterns", exception.message)
        }
    }

    @Test
    fun `single semicolon at end is allowed`() {
        val safeQueries = listOf(
            "SELECT * FROM users;",
            "INSERT INTO users VALUES (1, 'test');",
            "UPDATE users SET name = 'test';",
            "DELETE FROM users WHERE id = 1;"
        )

        safeQueries.forEach { query ->
            val safeSQL = when {
                query.lowercase().startsWith("select") -> SafeSQL.select(query)
                query.lowercase().startsWith("insert") -> SafeSQL.insert(query)
                query.lowercase().startsWith("update") -> SafeSQL.update(query)
                query.lowercase().startsWith("delete") -> SafeSQL.delete(query)
                else -> throw IllegalArgumentException("Unknown query type")
            }
            assertNotNull(safeSQL)
            assertEquals(query, safeSQL.query)
        }
    }

    @Test
    fun `dangerous patterns are rejected - comments`() {
        val dangerousQueries = listOf(
            "SELECT * FROM users -- comment",
            "SELECT * FROM users /* comment */",
            "SELECT * FROM users WHERE name = 'test' -- AND password = 'hack'"
        )

        dangerousQueries.forEach { query ->
            val exception = assertThrows<IllegalArgumentException> {
                SafeSQL.select(query)
            }
            assertEquals("SQL query contains potentially unsafe patterns", exception.message)
        }
    }

    @Test
    fun `dangerous patterns are rejected - stored procedures`() {
        val dangerousQueries = listOf(
            "SELECT * FROM users; EXEC xp_cmdshell 'dir'",
            "SELECT * FROM users; EXECUTE sp_configure",
            "SELECT xp_fileexist('c:\\test.txt')",
            "INSERT INTO users VALUES (1, 'test'); sp_adduser 'hacker'"
        )

        dangerousQueries.forEach { query ->
            val exception = assertThrows<IllegalArgumentException> {
                when {
                    query.lowercase().startsWith("select") -> SafeSQL.select(query)
                    query.lowercase().startsWith("insert") -> SafeSQL.insert(query)
                    else -> SafeSQL.select(query)
                }
            }
            assertEquals("SQL query contains potentially unsafe patterns", exception.message)
        }
    }

    @Test
    fun `dangerous patterns are rejected - DDL statements`() {
        val dangerousQueries = listOf(
            "SELECT * FROM users; DROP TABLE passwords",
            "SELECT * FROM users; CREATE USER hacker",
            "SELECT * FROM users; ALTER TABLE users ADD password VARCHAR(100)",
            "SELECT * FROM users; TRUNCATE TABLE logs",
            "SELECT * FROM users; GRANT ALL ON users TO hacker",
            "SELECT * FROM users; REVOKE SELECT ON users FROM user1"
        )

        dangerousQueries.forEach { query ->
            val exception = assertThrows<IllegalArgumentException> {
                SafeSQL.select(query)
            }
            assertEquals("SQL query contains potentially unsafe patterns", exception.message)
        }
    }

    @Test
    fun `case insensitive pattern matching`() {
        val dangerousQueries = listOf(
            "SELECT * FROM users; DROP table passwords",
            "SELECT * FROM users; drop TABLE passwords",
            "SELECT * FROM users; DROP TABLE passwords",
            "SELECT * FROM users WHERE name = 'test' EXEC xp_cmdshell",
            "SELECT * FROM USERS; EXECUTE SP_CONFIGURE"
        )

        dangerousQueries.forEach { query ->
            val exception = assertThrows<IllegalArgumentException> {
                SafeSQL.select(query)
            }
            assertEquals("SQL query contains potentially unsafe patterns", exception.message)
        }
    }

    @Test
    fun `valid complex queries are accepted`() {
        val validQueries = mapOf(
            "SELECT u.name, p.title FROM users u JOIN projects p ON u.id = p.user_id WHERE u.active = ?" to SafeSQL::select,
            "INSERT INTO audit_log (user_id, action, timestamp) VALUES (?, 'login', NOW())" to SafeSQL::insert,
            "UPDATE users SET last_login = NOW(), login_count = login_count + 1 WHERE id = ?" to SafeSQL::update,
            "DELETE FROM sessions WHERE expires_at < NOW() AND user_id = ?" to SafeSQL::delete
        )

        validQueries.forEach { (query, factory) ->
            val safeSQL = factory(query)
            assertNotNull(safeSQL)
            assertEquals(query, safeSQL.query)
        }
    }

    @Test
    fun `edge cases with whitespace and mixed case`() {
        val testCases = listOf(
            "  SELECT  *  FROM  users  " to SafeSQL::select,
            "\nSELECT * FROM users\n" to SafeSQL::select,
            "\t\tINSERT INTO users VALUES (1, 'test')\t\t" to SafeSQL::insert,
            "Update users set name = 'test'" to SafeSQL::update,
            "Delete from users where id = 1" to SafeSQL::delete
        )

        testCases.forEach { (query, factory) ->
            val safeSQL = factory(query)
            assertNotNull(safeSQL)
            assertEquals(query, safeSQL.query)
        }
    }

    @Test
    fun `query property returns original query`() {
        val originalQuery = "SELECT id, name FROM users WHERE active = true"
        val safeSQL = SafeSQL.select(originalQuery)
        assertEquals(originalQuery, safeSQL.query)
    }

    @Test
    fun `value class behavior - equals and hashCode`() {
        val query = "SELECT * FROM users"
        val safeSQL1 = SafeSQL.select(query)
        val safeSQL2 = SafeSQL.select(query)

        assertEquals(safeSQL1, safeSQL2)
        assertEquals(safeSQL1.hashCode(), safeSQL2.hashCode())
        assertEquals(safeSQL1.toString(), safeSQL2.toString())
    }

    @Test
    fun `parameters in queries are safe`() {
        val queriesWithParams = listOf(
            "SELECT * FROM users WHERE id = ? AND name = ?",
            "INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
            "UPDATE users SET name = ?, email = ? WHERE id = ?",
            "DELETE FROM users WHERE id = ? AND status = ?"
        )

        val factories = listOf(SafeSQL::select, SafeSQL::insert, SafeSQL::update, SafeSQL::delete)

        queriesWithParams.zip(factories).forEach { (query, factory) ->
            val safeSQL = factory(query)
            assertNotNull(safeSQL)
            assertEquals(query, safeSQL.query)
        }
    }

    @Test
    fun `column names containing dangerous keywords as substrings are allowed`() {
        val validQueries = listOf(
            "SELECT created_at FROM users WHERE updated_at > ?",
            "INSERT INTO logs (created_at, dropped_items) VALUES (?, ?)",
            "UPDATE users SET created_at = NOW() WHERE altered_by = ?",
            "DELETE FROM audit WHERE created_at < ? AND truncated_data IS NULL"
        )

        val factories = listOf(SafeSQL::select, SafeSQL::insert, SafeSQL::update, SafeSQL::delete)

        validQueries.zip(factories).forEach { (query, factory) ->
            val safeSQL = factory(query)
            assertNotNull(safeSQL)
            assertEquals(query, safeSQL.query)
        }
    }

    @Test
    fun `dangerous DDL keywords as whole words are still rejected`() {
        val dangerousQueries = listOf(
            "SELECT * FROM users; CREATE TABLE hacker (id INT)",
            "SELECT * FROM users; DROP TABLE passwords",
            "SELECT * FROM users; ALTER TABLE users ADD column password VARCHAR(100)",
            "SELECT * FROM users; TRUNCATE TABLE logs",
            "SELECT * FROM users; GRANT ALL PRIVILEGES ON users TO hacker",
            "SELECT * FROM users; REVOKE SELECT ON users FROM user"
        )

        dangerousQueries.forEach { query ->
            val exception = assertThrows<IllegalArgumentException> {
                SafeSQL.select(query)
            }
            assertEquals("SQL query contains potentially unsafe patterns", exception.message)
        }
    }
}
