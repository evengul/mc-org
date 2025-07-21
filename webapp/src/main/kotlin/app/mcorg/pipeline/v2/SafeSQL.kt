package app.mcorg.pipeline.v2

import org.intellij.lang.annotations.Language


/**
 * A type-safe wrapper for SQL queries that validates against common injection patterns.
 * This class ensures SQL queries are validated before execution.
 */
@JvmInline
value class SafeSQL private constructor(@Language("SQL") val query: String) {
    companion object {
        private val DANGEROUS_PATTERNS = listOf(
            ";", "--", "/*", "*/", "xp_", "sp_", "exec", "execute"
        ).map { it.lowercase() }

        // DDL keywords that should be matched as whole words to avoid false positives
        private val DDL_KEYWORDS = listOf(
            "drop", "alter", "create", "truncate", "grant", "revoke"
        ).map { it.lowercase() }

        fun select(@Language("SQL") query: String): SafeSQL {
            require(query.trim().lowercase().startsWith("select")) {
                "Query must be a SELECT statement"
            }
            return create(query)
        }

        fun insert(@Language("SQL") query: String): SafeSQL {
            require(query.trim().lowercase().startsWith("insert")) {
                "Query must be an INSERT statement"
            }
            return create(query)
        }

        fun update(@Language("SQL") query: String): SafeSQL {
            require(query.trim().lowercase().startsWith("update")) {
                "Query must be an UPDATE statement"
            }
            return create(query)
        }

        fun delete(@Language("SQL") query: String): SafeSQL {
            require(query.trim().lowercase().startsWith("delete")) {
                "Query must be a DELETE statement"
            }
            return create(query)
        }

        private fun create(@Language("SQL") query: String): SafeSQL {
            val lowercaseQuery = query.lowercase()

            // Check for basic dangerous patterns (comments, stored procedures, etc.)
            val hasBasicDangerousPattern = DANGEROUS_PATTERNS.any { pattern ->
                when (pattern) {
                    ";" -> query.count { it == ';' } > 1 // Allow single semicolon at end
                    else -> lowercaseQuery.contains(pattern)
                }
            }

            // Check for DDL keywords as whole words to avoid false positives
            val hasDangerousDDL = DDL_KEYWORDS.any { keyword ->
                // Use regex to match whole words only, with word boundaries
                val regex = "\\b$keyword\\b".toRegex()
                regex.containsMatchIn(lowercaseQuery)
            }

            require(!(hasBasicDangerousPattern || hasDangerousDDL)) {
                "SQL query contains potentially unsafe patterns"
            }

            return SafeSQL(query)
        }
    }
}
