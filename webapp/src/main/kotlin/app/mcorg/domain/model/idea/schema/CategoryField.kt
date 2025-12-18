package app.mcorg.domain.model.idea.schema

import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.presentation.templated.common.form.searchableselect.SearchableSelectOption
import kotlin.reflect.KClass

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
    open val parents: List<CategoryField>
) {
    abstract val isBottomLevelField: Boolean
    abstract val categoryValueClass: KClass<out CategoryValue>

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
    ) : CategoryField(key, label, searchable, filterable, required, helpText, parents) {
        override val isBottomLevelField = true
        override val categoryValueClass = CategoryValue.TextValue::class
    }

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
    ) : CategoryField(key, label, searchable, filterable, required, helpText, parents) {
        override val isBottomLevelField = true
        override val categoryValueClass = CategoryValue.IntValue::class
    }

    data class Select(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        override val parents: List<CategoryField> = emptyList(),
        private val options: List<SearchableSelectOption<String>>,
        val dynamicOptions: DynamicOptionsConfig? = null,
        val defaultValue: String? = null,
        var showSearchLimit: Int = 10
    ) : CategoryField(key, label, searchable, filterable, required, helpText,  parents) {
        override val isBottomLevelField = true
        override val categoryValueClass = CategoryValue.TextValue::class

        fun options(versionRange: MinecraftVersionRange = MinecraftVersionRange.Unbounded): List<SearchableSelectOption<String>> {
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
    ) : CategoryField(key, label, searchable, filterable, required, helpText, parents) {
        override val isBottomLevelField = true
        override val categoryValueClass = CategoryValue.MultiSelectValue::class
    }

    data class BooleanField(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        override val parents: List<CategoryField> = emptyList(),
        val defaultValue: Boolean = false
    ) : CategoryField(key, label, searchable, filterable, required, helpText, parents) {
        override val isBottomLevelField = true
        override val categoryValueClass = CategoryValue.BooleanValue::class
    }

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
    ) : CategoryField(key, label, searchable, filterable, required, helpText, parents) {
        override val isBottomLevelField = true
        override val categoryValueClass = CategoryValue.IntValue::class
    }

    data class StructField(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        override val parents: List<CategoryField> = emptyList(),
        val fields: List<CategoryField>
    ) : CategoryField(key, label, searchable, filterable, required, helpText, parents) {
        override val isBottomLevelField = false
        override val categoryValueClass = CategoryValue.MapValue::class
    }

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
    ) : CategoryField(key, label, searchable, filterable, required, helpText, parents) {
        override val isBottomLevelField = false
        override val categoryValueClass = CategoryValue.MapValue::class
    }

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
    ) : CategoryField(key, label, searchable, filterable, required, helpText, parents) {
        override val isBottomLevelField = true
        override val categoryValueClass = CategoryValue.MultiSelectValue::class
    }

    data class Percentage(
        override val key: String,
        override val label: String,
        override val searchable: Boolean = false,
        override val filterable: Boolean = false,
        override val required: Boolean = false,
        override val helpText: String? = null,
        override val parents: List<CategoryField> = emptyList(),
        val min: Int = 0,
        val max: Int = 100
    ) : CategoryField(key, label, searchable, filterable, required, helpText, parents) {
        override val isBottomLevelField = true
        override val categoryValueClass = CategoryValue.IntValue::class
    }

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

}
