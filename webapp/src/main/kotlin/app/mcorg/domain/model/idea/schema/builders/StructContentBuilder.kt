package app.mcorg.domain.model.idea.schema.builders

import app.mcorg.domain.model.idea.schema.CategoryField

@Suppress("UNUSED")
class StructContentBuilder(private val structField: CategoryField.StructField) {
    internal val fields: MutableList<CategoryField> = mutableListOf()

    fun textField(key: String, block: TextFieldBuilder.() -> Unit = {}) {
        fields += TextFieldBuilder(key).apply {
            parents = structField.parents + structField
            block()
        }.build()
    }

    fun numberField(key: String, block: NumberFieldBuilder.() -> Unit = {}) {
        fields += NumberFieldBuilder(key).apply {
            parents = structField.parents + structField
            block()
        }.build()
    }

    fun <T> selectField(key: String, block: SelectFieldBuilder<T>.() -> Unit) {
        fields += SelectFieldBuilder<T>(key).apply {
            parents = structField.parents + structField
            block()
        }.build()
    }

    fun multiSelectField(key: String, block: MultiSelectFieldBuilder.() -> Unit) {
        fields += MultiSelectFieldBuilder(key).apply {
            parents = structField.parents + structField
            block()
        }.build()
    }

    fun booleanField(key: String, block: BooleanFieldBuilder.() -> Unit = {}) {
        fields += BooleanFieldBuilder(key).apply {
            parents = structField.parents + structField
            block()
        }.build()
    }

    fun structField(key: String, block: StructFieldBuilder.() -> Unit = {}) {
        fields += StructFieldBuilder(key).apply {
            parents = structField.parents + structField
            block()
        }.build()
    }

    fun typedMapField(key: String, block: TypedMapFieldBuilder.() -> Unit = {}) {
        fields += TypedMapFieldBuilder(key).apply {
            parents = structField.parents + structField
            block()
        }.build()
    }

    fun listField(key: String, block: ListFieldBuilder.() -> Unit = {}) {
        fields += ListFieldBuilder(key).apply {
            parents = structField.parents + structField
            block()
        }.build()
    }

    fun percentageField(key: String, block: PercentageFieldBuilder.() -> Unit = {}) {
        fields += PercentageFieldBuilder(key).apply {
            parents = structField.parents + structField
            block()
        }.build()
    }
}