package app.mcorg.domain.model.idea.schema

import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.presentation.templated.common.form.searchableselect.SearchableSelectOption

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
    open val helpText: String?,
    open val isBottomLevelField: Boolean,
    open val parents: List<CategoryField>
) {
    data class Text(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        override val parents: List<CategoryField> = emptyList(),
        val placeholder: String? = null,
        val maxLength: Int? = null,
        val multiline: Boolean = false
    ) : CategoryField(key, label, searchable, filterable, required, helpText, true, parents)

    data class Number(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        override val parents: List<CategoryField> = emptyList(),
        val min: Double? = null,
        val max: Double? = null,
        val suffix: String? = null,
        val step: Double? = null
    ) : CategoryField(key, label, searchable, filterable, required, helpText, true, parents)

    data class Select<T>(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        override val parents: List<CategoryField> = emptyList(),
        private val options: List<SearchableSelectOption<T>>,
        val dynamicOptions: DynamicOptionsConfig<T>? = null,
        val defaultValue: String? = null,
        var showSearchLimit: Int = 10
    ) : CategoryField(key, label, searchable, filterable, required, helpText, true, parents) {
        fun options(versionRange: MinecraftVersionRange = MinecraftVersionRange.Unbounded): List<SearchableSelectOption<T>> {
            return dynamicOptions?.resolve(versionRange)?.ifEmpty { options } ?: options
        }
    }

    data class MultiSelect(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        override val parents: List<CategoryField> = emptyList(),
        val options: List<String>,
        val minSelections: Int? = null,
        val maxSelections: Int? = null
    ) : CategoryField(key, label, searchable, filterable, required, helpText, true, parents)

    data class BooleanField(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        override val parents: List<CategoryField> = emptyList(),
        val defaultValue: Boolean = false
    ) : CategoryField(key, label, searchable, filterable, required, helpText, true, parents)

    data class Rate(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        override val parents: List<CategoryField> = emptyList(),
        val unit: String = "items/hour",
        val min: Double? = null,
        val max: Double? = null
    ) : CategoryField(key, label, searchable, filterable, required, helpText, true, parents)

    data class StructField(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        override val parents: List<CategoryField> = emptyList(),
        val fields: List<CategoryField>
    ) : CategoryField(key, label, searchable, filterable, required, helpText, false, parents)

    data class TypedMapField(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        override val parents: List<CategoryField> = emptyList(),
        val keyType: CategoryField,
        val valueType: CategoryField
    ) : CategoryField(key, label, searchable, filterable, required, helpText, false, parents)

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
        override val parents: List<CategoryField> = emptyList(),
        val itemLabel: String = "Item"
    ) : CategoryField(key, label, searchable, filterable, required, helpText, true, parents)

    data class Percentage(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        override val parents: List<CategoryField> = emptyList(),
        val min: Double = 0.0,
        val max: Double = 1.0
    ) : CategoryField(key, label, searchable, filterable, required, helpText, true, parents)

    fun getCompleteKey(): String {
        return "categoryData." + if (parents.isNotEmpty()) {
            parents.joinToString(".") { it.key } + ".$key" + keyPostfix()
        } else {
            key + keyPostfix()
        }
    }

    private fun keyPostfix(): String {
        if (this is MultiSelect || this.parents.firstOrNull() is TypedMapField) {
            return "[]"
        }
        return ""
    }

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
            is MultiSelect -> if (value is Set<*>) {
                return value.joinToString(", ")
            }
            is BooleanField -> if (value is Boolean) {
                return if (value) "Yes" else "No"
            }
            is Select<*> -> {
                val option = options().find { it.value.toString() == value?.toString() }
                return option?.label ?: value?.toString() ?: ""
            }
            else -> value?.toString() ?: ""
        }
        return value?.toString() ?: ""
    }
}
