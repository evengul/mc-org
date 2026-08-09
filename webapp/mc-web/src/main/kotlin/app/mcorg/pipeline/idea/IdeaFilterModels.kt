package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty

/**
 * Represents all possible filter criteria for idea search
 */
data class IdeaSearchFilters(
    // Base filters (always available)
    val query: String? = null,                      // Full-text search on name + description
    val category: IdeaCategory? = null,
    val difficulties: List<IdeaDifficulty> = emptyList(),
    val minRating: Double? = null,
    val minecraftVersion: String? = null,           // Will check compatibility

    // Category-specific filters (JSONB field queries)
    val categoryFilters: Map<String, FilterValue> = emptyMap(),

    /**
     * Who is looking (MCO-291). The hub shows public ideas plus the viewer's own private designs.
     * Not parsed from query parameters — it comes from the authenticated call, never the client.
     */
    val viewerId: Int? = null,

    // Pagination
    val page: Int = 1,
    val pageSize: Int = 20
)

/**
 * Represents a parsed filter value from query parameters
 */
sealed interface FilterValue {
    data class TextValue(val value: String) : FilterValue
    data class NumberRange(val min: Double?, val max: Double?) : FilterValue
    data class BooleanValue(val value: Boolean) : FilterValue
    data class SelectValue(val value: String) : FilterValue
    data class MultiSelectValue(val values: List<String>) : FilterValue
}

