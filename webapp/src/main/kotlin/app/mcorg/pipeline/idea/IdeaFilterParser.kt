package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.schema.CategoryField
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import io.ktor.http.Parameters

/**
 * Parses query parameters into structured IdeaSearchFilters with validation
 */
object IdeaFilterParser {

    /**
     * Parses query parameters into structured IdeaSearchFilters
     */
    fun parse(parameters: Parameters): IdeaSearchFilters {
        val query = parameters["query"]?.takeIf { it.isNotBlank() }
        val category = parseCategory(parameters)
        val difficulties = parseDifficulties(parameters)
        val minRating = parseMinRating(parameters)
        val minecraftVersion = parameters["minecraftVersion"]?.takeIf { it.isNotBlank() }
        val categoryFilters = parseCategoryFilters(parameters, category)

        return IdeaSearchFilters(
            query = query,
            category = category,
            difficulties = difficulties,
            minRating = minRating,
            minecraftVersion = minecraftVersion,
            categoryFilters = categoryFilters
        )
    }

    /**
     * Parses category parameter
     */
    private fun parseCategory(parameters: Parameters): IdeaCategory? {
        val categoryParam = parameters["category"]?.takeIf { it.isNotEmpty() }
        return try {
            categoryParam?.let { IdeaCategory.valueOf(it.uppercase()) }
        } catch (_: IllegalArgumentException) {
            null // Invalid category, ignore
        }
    }

    /**
     * Parses difficulty checkboxes (can be multiple)
     */
    private fun parseDifficulties(parameters: Parameters): List<IdeaDifficulty> {
        val difficultyParams = parameters.getAll("difficulty[]") ?: emptyList()
        return difficultyParams.mapNotNull { param ->
            try {
                IdeaDifficulty.valueOf(param.uppercase())
            } catch (_: IllegalArgumentException) {
                null // Invalid difficulty, ignore
            }
        }
    }

    /**
     * Parses minimum rating filter
     */
    private fun parseMinRating(parameters: Parameters): Double? {
        val ratingParam = parameters["minRating"]?.takeIf { it.isNotBlank() }
        return try {
            ratingParam?.toDouble()?.coerceIn(0.0, 5.0)
        } catch (_: NumberFormatException) {
            null // Invalid rating, ignore
        }
    }

    /**
     * Parses category-specific filters with validation against schema
     */
    private fun parseCategoryFilters(
        parameters: Parameters,
        category: IdeaCategory?
    ): Map<String, FilterValue> {
        if (category == null) return emptyMap()

        val schema = IdeaCategorySchemas.getSchema(category)
        val filterableFields = schema.getFilterableFields()
        val filters = mutableMapOf<String, FilterValue>()

        filterableFields.forEach { field ->
            val filterValue = parseFieldFilter(parameters, field)
            if (filterValue != null) {
                filters[field.key] = filterValue
            }
        }

        return filters
    }

    /**
     * Parses a filter value based on field type
     */
    private fun parseFieldFilter(parameters: Parameters, field: CategoryField): FilterValue? {
        return when (field) {
            is CategoryField.Text -> parseTextField(parameters, field)
            is CategoryField.Number -> parseNumberField(parameters, field)
            is CategoryField.Rate -> parseRateField(parameters, field)
            is CategoryField.Percentage -> parsePercentageField(parameters, field)
            is CategoryField.Select -> parseSelectField(parameters, field)
            is CategoryField.MultiSelect -> parseMultiSelectField(parameters, field)
            is CategoryField.BooleanField -> parseBooleanField(parameters, field)
            else -> null // Unsupported field type for filtering
        }
    }

    /**
     * Parse text field filter
     */
    private fun parseTextField(parameters: Parameters, field: CategoryField.Text): FilterValue? {
        val value = parameters["categoryFilters[${field.key}]"]?.takeIf { it.isNotBlank() }
        return value?.let { FilterValue.TextValue(it) }
    }

    /**
     * Parse number range filter (min/max)
     */
    private fun parseNumberField(parameters: Parameters, field: CategoryField.Number): FilterValue? {
        val minParam = parameters["categoryFilters[${field.key}_min]"]
        val maxParam = parameters["categoryFilters[${field.key}_max]"]

        val min = minParam?.toDoubleOrNull()?.let { value ->
            field.min?.let { minBound -> value.coerceAtLeast(minBound) } ?: value
        }
        val max = maxParam?.toDoubleOrNull()?.let { value ->
            field.max?.let { maxBound -> value.coerceAtMost(maxBound) } ?: value
        }

        return if (min != null || max != null) {
            FilterValue.NumberRange(min, max)
        } else {
            null
        }
    }

    /**
     * Parse rate field filter (similar to number)
     */
    private fun parseRateField(parameters: Parameters, field: CategoryField.Rate): FilterValue? {
        val minParam = parameters["categoryFilters[${field.key}_min]"]
        val maxParam = parameters["categoryFilters[${field.key}_max]"]

        val min = minParam?.toDoubleOrNull()?.let { value ->
            field.min?.let { minBound -> value.coerceAtLeast(minBound) } ?: value
        }
        val max = maxParam?.toDoubleOrNull()?.let { value ->
            field.max?.let { maxBound -> value.coerceAtMost(maxBound) } ?: value
        }

        return if (min != null || max != null) {
            FilterValue.NumberRange(min, max)
        } else {
            null
        }
    }

    /**
     * Parse percentage field filter (0.0 to 1.0)
     */
    private fun parsePercentageField(parameters: Parameters, field: CategoryField.Percentage): FilterValue? {
        val minParam = parameters["categoryFilters[${field.key}_min]"]
        val maxParam = parameters["categoryFilters[${field.key}_max]"]

        val min = minParam?.toDoubleOrNull()?.coerceIn(0.0, 1.0)
        val max = maxParam?.toDoubleOrNull()?.coerceIn(0.0, 1.0)

        return if (min != null || max != null) {
            FilterValue.NumberRange(min, max)
        } else {
            null
        }
    }

    /**
     * Parse select field filter
     */
    private fun parseSelectField(parameters: Parameters, field: CategoryField.Select): FilterValue? {
        val value = parameters["categoryFilters[${field.key}]"]?.takeIf { it.isNotBlank() }
        return if (value != null && field.options().map { it.value }.contains(value)) {
            FilterValue.SelectValue(value)
        } else {
            null
        }
    }

    /**
     * Parse multi-select field filter (checkboxes)
     */
    private fun parseMultiSelectField(parameters: Parameters, field: CategoryField.MultiSelect): FilterValue? {
        val values = parameters.getAll("categoryFilters[${field.key}][]") ?: emptyList()
        val validValues = values.filter { field.options.contains(it) }

        return if (validValues.isNotEmpty()) {
            FilterValue.MultiSelectValue(validValues)
        } else {
            null
        }
    }

    /**
     * Parse boolean field filter
     */
    private fun parseBooleanField(parameters: Parameters, field: CategoryField.BooleanField): FilterValue? {
        val value = parameters["categoryFilters[${field.key}]"]
        return if (value == "true") {
            FilterValue.BooleanValue(true)
        } else {
            null // Unchecked checkboxes are not sent, so we only filter when checked
        }
    }

}

