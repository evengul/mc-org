package app.mcorg.domain.model.idea.schema.builders

import app.mcorg.domain.model.idea.schema.CategoryField

@Suppress("UNUSED")
class TypedMapFieldContentBuilder(private val mapField: CategoryField.TypedMapField){
    internal var keyField: CategoryField = mapField.keyType
    internal var valueField: CategoryField = mapField.valueType

    fun textKey(block: TextFieldBuilder.() -> Unit = {}) {
        keyField = textField("key", block)
    }

    fun numberKey(block: NumberFieldBuilder.() -> Unit = {}) {
        keyField = numberField("key", block)
    }

    fun selectKey(block: SelectFieldBuilder.() -> Unit) {
        keyField = selectField("key", block)
    }

    fun booleanKey(block: BooleanFieldBuilder.() -> Unit = {}) {
        keyField = booleanField("key", block)
    }

    fun textValue(block: TextFieldBuilder.() -> Unit = {}) {
        valueField = textField("value", block)
    }

    fun numberValue(block: NumberFieldBuilder.() -> Unit = {}) {
        valueField = numberField("value", block)
    }

    fun selectValue(block: SelectFieldBuilder.() -> Unit) {
        valueField = selectField("value", block)
    }

    fun multiSelectValue(block: MultiSelectFieldBuilder.() -> Unit) {
        valueField = multiSelectField("value", block)
    }

    fun booleanValue(block: BooleanFieldBuilder.() -> Unit = {}) {
        valueField = booleanField("value", block)
    }

    fun structValue(block: StructFieldBuilder.() -> Unit = {}) {
        valueField = structField("value", block)
    }

    fun typedMapValue(block: TypedMapFieldBuilder.() -> Unit = {}) {
        valueField = typedMapField("value", block)
    }

    fun listValue(block: ListFieldBuilder.() -> Unit = {}) {
        valueField = listField("value", block)
    }

    fun rateValue(block: RateFieldBuilder.() -> Unit = {}) {
        valueField = rateField("value", block)
    }

    fun percentageValue(block: PercentageFieldBuilder.() -> Unit = {}) {
        valueField = percentageField("value", block)
    }

    private fun textField(key: String, block: TextFieldBuilder.() -> Unit = {}): CategoryField.Text {
        return TextFieldBuilder(key).apply {
            parents = mapField.parents + mapField
            block()
        }.build()
    }

    private fun numberField(key: String, block: NumberFieldBuilder.() -> Unit = {}): CategoryField.Number {
        return NumberFieldBuilder(key).apply {
            parents = mapField.parents + mapField
            block()
        }.build()
    }

    private fun rateField(key: String, block: RateFieldBuilder.() -> Unit = {}): CategoryField.Rate {
        return RateFieldBuilder(key).apply {
            parents = mapField.parents + mapField
            block()
        }.build()
    }

    private fun selectField(key: String, block: SelectFieldBuilder.() -> Unit): CategoryField.Select {
        return SelectFieldBuilder(key).apply {
            parents = mapField.parents + mapField
            block()
        }.build()
    }

    private fun multiSelectField(key: String, block: MultiSelectFieldBuilder.() -> Unit): CategoryField.MultiSelect {
        return MultiSelectFieldBuilder(key).apply {
            parents = mapField.parents + mapField
            block()
        }.build()
    }

    private fun booleanField(key: String, block: BooleanFieldBuilder.() -> Unit = {}): CategoryField.BooleanField {
        return BooleanFieldBuilder(key).apply {
            parents = mapField.parents + mapField
            block()
        }.build()
    }

    private fun structField(key: String, block: StructFieldBuilder.() -> Unit = {}): CategoryField.StructField {
        return StructFieldBuilder(key).apply {
            parents = mapField.parents + mapField
            block()
        }.build()
    }

    private fun typedMapField(key: String, block: TypedMapFieldBuilder.() -> Unit = {}): CategoryField.TypedMapField {
        return TypedMapFieldBuilder(key).apply {
            parents = mapField.parents + mapField
            block()
        }.build()
    }

    private fun listField(key: String, block: ListFieldBuilder.() -> Unit = {}): CategoryField.ListField {
        return ListFieldBuilder(key).apply {
            parents = mapField.parents + mapField
            block()
        }.build()
    }

    private fun percentageField(key: String, block: PercentageFieldBuilder.() -> Unit = {}): CategoryField.Percentage {
        return PercentageFieldBuilder(key).apply {
            parents = mapField.parents + mapField
            block()
        }.build()
    }
}