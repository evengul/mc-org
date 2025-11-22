package app.mcorg.domain.model.idea.schema.builders

import app.mcorg.domain.model.idea.schema.CategoryField
import app.mcorg.domain.model.idea.schema.DynamicOptionsConfig
import app.mcorg.presentation.templated.common.form.searchableselect.SearchableSelectOption

/**
 * Base builder class for all field types.
 * Provides common properties like label, searchable, filterable, etc.
 */
abstract class FieldBuilder<out T : CategoryField>(protected val key: String) {
    var label: String = key.replace(Regex("([A-Z])"), " $1").trim()
        .split(" ")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    var searchable: Boolean = false
    var filterable: Boolean = false
    var required: Boolean = false
    var helpText: String? = null

    abstract fun build(): T
}

class TextFieldBuilder(key: String) : FieldBuilder<CategoryField.Text>(key) {
    var placeholder: String? = null
    var maxLength: Int? = null
    var multiline: Boolean = false
    var parents: List<CategoryField> = emptyList()

    override fun build() = CategoryField.Text(
        key, label, searchable, filterable, required, helpText, parents, placeholder, maxLength, multiline
    )
}

class NumberFieldBuilder(key: String) : FieldBuilder<CategoryField.Number>(key) {
    var min: Double? = null
    var max: Double? = null
    var suffix: String? = null
    var step: Double? = null
    var parents: List<CategoryField> = emptyList()

    override fun build() = CategoryField.Number(
        key, label, searchable, filterable, required, helpText, parents, min, max, suffix, step
    )
}

class SelectFieldBuilder<T>(key: String) : FieldBuilder<CategoryField.Select<T>>(key) {
    var options: List<SearchableSelectOption<T>> = emptyList()
    var defaultValue: String? = null
    var parents: List<CategoryField> = emptyList()
    var dynamicOptionsConfig: DynamicOptionsConfig<T>? = null
    var showSearchLimit: Int = 10

    override fun build() = CategoryField.Select(
        key, label, searchable, filterable, required, helpText, parents, options, dynamicOptionsConfig, defaultValue, showSearchLimit
    )
}

class MultiSelectFieldBuilder(key: String) : FieldBuilder<CategoryField.MultiSelect>(key) {
    var options: List<String> = emptyList()
    var minSelections: Int? = null
    var maxSelections: Int? = null
    var parents: List<CategoryField> = emptyList()

    override fun build() = CategoryField.MultiSelect(
        key, label, searchable, filterable, required, helpText, parents, options, minSelections, maxSelections
    )
}

class BooleanFieldBuilder(key: String) : FieldBuilder<CategoryField.BooleanField>(key) {
    var defaultValue: Boolean = false
    var parents: List<CategoryField> = emptyList()

    override fun build() = CategoryField.BooleanField(
        key, label, searchable, filterable, required, helpText, parents, defaultValue
    )
}

class RateFieldBuilder(key: String) : FieldBuilder<CategoryField.Rate>(key) {
    var unit: String = "items/hour"
    var min: Double? = null
    var max: Double? = null
    var parents: List<CategoryField> = emptyList()

    override fun build() = CategoryField.Rate(
        key, label, searchable, filterable, required, helpText, parents, unit, min, max
    )
}

class StructFieldBuilder(key: String) : FieldBuilder<CategoryField.StructField>(key) {
    var fields: List<CategoryField> = listOf()
    var parents: List<CategoryField> = emptyList()

    var fieldBuilder: StructContentBuilder.() -> Unit = {}

    fun fields(block: StructContentBuilder.() -> Unit) {
        fieldBuilder = block
    }

    override fun build(): CategoryField.StructField {
        val structField = CategoryField.StructField(
            key, label, searchable, filterable, required, helpText, parents, fields
        )
        val contentBuilder = StructContentBuilder(structField)
        contentBuilder.apply(fieldBuilder)
        return structField.copy(fields = contentBuilder.fields)
    }
}

class TypedMapFieldBuilder(key: String) : FieldBuilder<CategoryField.TypedMapField>(key) {
    var parents: List<CategoryField> = emptyList()
    var keyType: CategoryField = CategoryField.Text(
        key = "key",
        label = label,
    )

    var valueType: CategoryField = CategoryField.Text(
        key = "value",
        label = label,
    )

    var typeBuilder : TypedMapFieldContentBuilder.() -> Unit = {}

    fun types(block: TypedMapFieldContentBuilder.() -> Unit) {
        typeBuilder = block
    }

    override fun build(): CategoryField.TypedMapField {
        val typedMapField = CategoryField.TypedMapField(
            key, label, searchable, filterable, required, helpText, parents, keyType, valueType
        )
        val contentBuilder = TypedMapFieldContentBuilder(typedMapField)
        contentBuilder.apply(typeBuilder)
        return typedMapField.copy(
            keyType = contentBuilder.keyField,
            valueType = contentBuilder.valueField
        )
    }
}

class ListFieldBuilder(key: String) : FieldBuilder<CategoryField.ListField>(key) {
    var itemLabel: String = "Item"
    var parents: List<CategoryField> = emptyList()

    override fun build() = CategoryField.ListField(
        key, label, searchable, filterable, required, helpText, parents, itemLabel
    )
}

class PercentageFieldBuilder(key: String) : FieldBuilder<CategoryField.Percentage>(key) {
    var min: Double = 0.0
    var max: Double = 1.0
    var parents: List<CategoryField> = emptyList()

    override fun build() = CategoryField.Percentage(
        key, label, searchable, filterable, required, helpText, parents, min, max
    )
}
