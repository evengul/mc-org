package app.mcorg.pipeline

import org.intellij.lang.annotations.Language

/**
 * The SQL text a [DatabaseSteps] step executes.
 *
 * What makes a query safe is that its text is a compile-time constant and every value reaches
 * PostgreSQL through a `?` bind — not that a validator waved it through. This class cannot check
 * that: by the time a `String` arrives here, any interpolation has already happened. So the
 * boundary is enforced where it is visible, at the source. `SafeSqlSourceScanTest` reads every
 * `SafeSQL.select/with/insert/update/delete(...)` call under `src/main` and fails on an argument
 * that is not a single string literal, or that interpolates, unless the site is on that test's
 * allowlist with a stated reason. Adding an interpolation is therefore a reviewed decision rather
 * than a precedent that lands silently — MCO-435 found three that had (MCO-551).
 *
 * What is still checked here is cheap and about *shape*: the factory names the statement kind, so
 * `select("UPDATE ...")` fails at construction, and a `SafeSQL` is one statement — a `;` anywhere
 * but the very end is refused, so no instance can carry a batch. Until MCO-551 there was also a
 * substring denylist (`xp_`, `sp_`, `exec`, whole-word DDL keywords). It came from SQL Server
 * folklore, guarded nothing on PostgreSQL, did nothing about interpolation, and would have
 * rejected a legitimate column called `executed_at`. The scan test is what it pretended to be.
 */
@JvmInline
value class SafeSQL private constructor(@param:Language("SQL") val query: String) {
    companion object {
        private val QUOTED_LITERAL = Regex("'(?:[^']|'')*'")
        private val LINE_COMMENT = Regex("--[^\\n]*")

        fun select(@Language("SQL") query: String): SafeSQL = of(query, "select", "Query must be a SELECT statement")
        fun with(@Language("SQL") query: String): SafeSQL = of(query, "with", "Query must be a WITH statement")
        fun insert(@Language("SQL") query: String): SafeSQL = of(query, "insert", "Query must be an INSERT statement")
        fun update(@Language("SQL") query: String): SafeSQL = of(query, "update", "Query must be an UPDATE statement")
        fun delete(@Language("SQL") query: String): SafeSQL = of(query, "delete", "Query must be a DELETE statement")

        private fun of(query: String, keyword: String, wrongKind: String): SafeSQL {
            val trimmed = query.trim()
            require(trimmed.lowercase().startsWith(keyword)) { wrongKind }
            // A ';' inside a quoted literal or a `--` comment is text, not a statement boundary.
            val outsideText = trimmed.removeSuffix(";").replace(QUOTED_LITERAL, "").replace(LINE_COMMENT, "")
            require(!outsideText.contains(';')) { "SafeSQL holds a single statement; a ';' may only end it" }
            return SafeSQL(query)
        }
    }
}
