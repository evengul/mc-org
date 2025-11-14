package app.mcorg.domain.model.idea.schema

/**
 * Sealed class representing different field types in category schemas.
 * Each field type has specific validation rules and rendering requirements.
 */
sealed class CategoryField(
    open val key: String,
    open val label: String,
    open val searchable: Boolean,
    open val filterable: Boolean,
    open val required: Boolean,
    open val helpText: String?
) {
    data class Text(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val placeholder: String? = null,
        val maxLength: Int? = null,
        val multiline: Boolean = false
    ) : CategoryField(key, label, searchable, filterable, required, helpText)

    data class Number(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val min: Double? = null,
        val max: Double? = null,
        val suffix: String? = null,
        val step: Double? = null
    ) : CategoryField(key, label, searchable, filterable, required, helpText)

    data class Select(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val options: List<String>,
        val defaultValue: String? = null
    ) : CategoryField(key, label, searchable, filterable, required, helpText)

    data class MultiSelect(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val options: List<String>,
        val minSelections: Int? = null,
        val maxSelections: Int? = null
    ) : CategoryField(key, label, searchable, filterable, required, helpText)

    data class BooleanField(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val defaultValue: Boolean = false
    ) : CategoryField(key, label, searchable, filterable, required, helpText)

    data class Rate(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val unit: String = "items/hour",
        val min: Double? = null,
        val max: Double? = null
    ) : CategoryField(key, label, searchable, filterable, required, helpText)

    data class MapField(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val keyLabel: String = "Key",
        val valueLabel: String = "Value",
        val keyOptions: List<String>? = null
    ) : CategoryField(key, label, searchable, filterable, required, helpText)

    /**
     * Used for free-form lists with comma-separated values.
     * @see:MultiSelect for predefined option lists.
     */
    data class ListField(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val itemLabel: String = "Item"
    ) : CategoryField(key, label, searchable, filterable, required, helpText)

    data class Percentage(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        val min: Double = 0.0,
        val max: Double = 1.0
    ) : CategoryField(key, label, searchable, filterable, required, helpText)

    fun displayValue(value: Any?): String {
        when (this) {
            is Rate -> if (value is Int) {
                return "$value $unit"
            }
            is Percentage -> if (value is Int) {
                val percentage = if (max <= 1.0) {
                    value.toDouble() * 100.0
                } else {
                    value.toDouble()
                }
                return "%.2f%%".format(percentage)
            }
            is ListField -> if (value is List<*>) {
                return value.joinToString(", ")
            }
            is MapField -> if (value is Map<*, *>) {
                return value.entries.joinToString(", ") { (k, v) -> "$k: $v" }
            }
            is MultiSelect -> if (value is Set<*>) {
                return value.joinToString(", ")
            }
            is BooleanField -> if (value is Boolean) {
                return if (value) "Yes" else "No"
            }
            else -> value?.toString() ?: ""
        }
        return value?.toString() ?: ""
    }
}
