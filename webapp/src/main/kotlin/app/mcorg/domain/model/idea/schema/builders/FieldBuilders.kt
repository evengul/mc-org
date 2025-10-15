package app.mcorg.domain.model.idea.schema.builders

import app.mcorg.domain.model.idea.schema.CategoryField

/**
 * Base builder class for all field types.
 * Provides common properties like label, searchable, filterable, etc.
 */
abstract class FieldBuilder<T : CategoryField>(protected val key: String) {
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

    override fun build() = CategoryField.Text(
        key, label, searchable, filterable, required, helpText, placeholder, maxLength, multiline
    )
}

class NumberFieldBuilder(key: String) : FieldBuilder<CategoryField.Number>(key) {
    var min: Double? = null
    var max: Double? = null
    var suffix: String? = null
    var step: Double? = null

    override fun build() = CategoryField.Number(
        key, label, searchable, filterable, required, helpText, min, max, suffix, step
    )
}

class SelectFieldBuilder(key: String) : FieldBuilder<CategoryField.Select>(key) {
    var options: List<String> = emptyList()
    var defaultValue: String? = null

    override fun build() = CategoryField.Select(
        key, label, searchable, filterable, required, helpText, options, defaultValue
    )
}

class MultiSelectFieldBuilder(key: String) : FieldBuilder<CategoryField.MultiSelect>(key) {
    var options: List<String> = emptyList()
    var minSelections: Int? = null
    var maxSelections: Int? = null

    override fun build() = CategoryField.MultiSelect(
        key, label, searchable, filterable, required, helpText, options, minSelections, maxSelections
    )
}

class BooleanFieldBuilder(key: String) : FieldBuilder<CategoryField.BooleanField>(key) {
    var defaultValue: Boolean = false

    override fun build() = CategoryField.BooleanField(
        key, label, searchable, filterable, required, helpText, defaultValue
    )
}

class RateFieldBuilder(key: String) : FieldBuilder<CategoryField.Rate>(key) {
    var unit: String = "items/hour"
    var min: Double? = null
    var max: Double? = null

    override fun build() = CategoryField.Rate(
        key, label, searchable, filterable, required, helpText, unit, min, max
    )
}

class DimensionsFieldBuilder(key: String) : FieldBuilder<CategoryField.Dimensions>(key) {
    override fun build() = CategoryField.Dimensions(
        key, label, searchable, filterable, required, helpText
    )
}

class MapFieldBuilder(key: String) : FieldBuilder<CategoryField.MapField>(key) {
    var keyLabel: String = "Key"
    var valueLabel: String = "Value"
    var keyOptions: List<String>? = null

    override fun build() = CategoryField.MapField(
        key, label, searchable, filterable, required, helpText, keyLabel, valueLabel, keyOptions
    )
}

class ListFieldBuilder(key: String) : FieldBuilder<CategoryField.ListField>(key) {
    var itemLabel: String = "Item"
    var allowedValues: List<String>? = null

    override fun build() = CategoryField.ListField(
        key, label, searchable, filterable, required, helpText, itemLabel, allowedValues
    )
}

class PercentageFieldBuilder(key: String) : FieldBuilder<CategoryField.Percentage>(key) {
    var min: Double = 0.0
    var max: Double = 1.0

    override fun build() = CategoryField.Percentage(
        key, label, searchable, filterable, required, helpText, min, max
    )
}
