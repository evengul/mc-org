package app.mcorg.pipeline.idea

/**
 * Builds dynamic SQL WHERE clauses for idea filtering
 */
object IdeaSqlBuilder {

    /**
     * Builds WHERE clause and parameter list from filters
     */
    fun buildWhereClause(filters: IdeaSearchFilters): SqlWhereClause {
        val conditions = mutableListOf<String>()
        val parameters = mutableListOf<Any>()

        // Base filters
        filters.category?.let {
            conditions.add("i.category = ?")
            parameters.add(it.name)
        }

        if (filters.difficulties.isNotEmpty()) {
            conditions.add("i.difficulty = ANY(?)")
            parameters.add(filters.difficulties.map { it.name }.toTypedArray())
        }

        filters.minRating?.let {
            conditions.add("i.rating_average >= ?")
            parameters.add(it)
        }

        // Full-text search on name and description
        filters.query?.let {
            conditions.add("to_tsvector('english', i.name || ' ' || i.description) @@ plainto_tsquery('english', ?)")
            parameters.add(it)
        }

        // Category-specific JSONB filters
        filters.categoryFilters.forEach { (key, value) ->
            val (condition, params) = buildJsonbCondition(key, value)
            if (condition.isNotEmpty()) {
                conditions.add(condition)
                parameters.addAll(params)
            }
        }

        return SqlWhereClause(
            whereClause = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}",
            parameters = parameters
        )
    }

    /**
     * Builds JSONB filter condition based on field type
     */
    private fun buildJsonbCondition(key: String, value: FilterValue): Pair<String, List<Any>> {
        return when (value) {
            is FilterValue.TextValue -> {
                // Case-insensitive text search in JSONB field
                "i.category_data->'$key'->>'value' ILIKE ?" to listOf("%${value.value}%")
            }

            is FilterValue.NumberRange -> {
                val conditions = mutableListOf<String>()
                val params = mutableListOf<Any>()

                value.min?.let {
                    conditions.add("(i.category_data->'$key'->>'value')::numeric >= ?")
                    params.add(it)
                }
                value.max?.let {
                    conditions.add("(i.category_data->'$key'->>'value')::numeric <= ?")
                    params.add(it)
                }

                if (conditions.isEmpty()) {
                    "" to emptyList()
                } else {
                    "(${conditions.joinToString(" AND ")})" to params
                }
            }

            is FilterValue.BooleanValue -> {
                // JSONB boolean comparison (stored as JSON boolean)
                "(i.category_data->'$key'->>'value')::boolean = ?" to listOf(value.value)
            }

            is FilterValue.SelectValue -> {
                // Exact match for select field
                "i.category_data->'$key'->>'value' = ?" to listOf(value.value)
            }

            is FilterValue.MultiSelectValue -> {
                // Check if JSONB array contains any of the selected values
                // Use EXISTS with jsonb_array_elements_text to check for overlap
                val placeholders = value.values.joinToString(",") { "?" }
                "EXISTS (SELECT 1 FROM jsonb_array_elements_text(i.category_data->'$key'->'values') AS elem WHERE elem IN ($placeholders))" to value.values
            }
        }
    }
}

/**
 * Container for SQL WHERE clause and its parameters
 */
data class SqlWhereClause(
    val whereClause: String,
    val parameters: List<Any>
)
