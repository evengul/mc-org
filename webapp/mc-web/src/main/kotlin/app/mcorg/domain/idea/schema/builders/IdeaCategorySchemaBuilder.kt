package app.mcorg.domain.model.idea.schema.builders

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.CategoryField
import app.mcorg.domain.model.idea.schema.IdeaCategorySchema

/**
 * DSL Builder for creating category schemas.
 * Provides a clean, type-safe way to define category-specific fields.
 */
class IdeaCategorySchemaBuilder(private val category: IdeaCategory) {
    private val fields = mutableListOf<CategoryField>()

    fun textField(key: String, block: TextFieldBuilder.() -> Unit = {}) {
        fields += TextFieldBuilder(key).apply(block).build()
    }

    fun numberField(key: String, block: NumberFieldBuilder.() -> Unit = {}) {
        fields += NumberFieldBuilder(key).apply(block).build()
    }

    fun selectField(key: String, block: SelectFieldBuilder.() -> Unit) {
        fields += SelectFieldBuilder(key).apply(block).build()
    }

    fun multiSelectField(key: String, block: MultiSelectFieldBuilder.() -> Unit) {
        fields += MultiSelectFieldBuilder(key).apply(block).build()
    }

    fun booleanField(key: String, block: BooleanFieldBuilder.() -> Unit = {}) {
        fields += BooleanFieldBuilder(key).apply(block).build()
    }

    fun structField(key: String, block: StructFieldBuilder.() -> Unit = {}) {
        fields += StructFieldBuilder(key).apply(block).build()
    }

    fun typedMapField(key: String, block: TypedMapFieldBuilder.() -> Unit = {}) {
        fields += TypedMapFieldBuilder(key).apply(block).build()
    }

    fun listField(key: String, block: ListFieldBuilder.() -> Unit = {}) {
        fields += ListFieldBuilder(key).apply(block).build()
    }

    fun percentageField(key: String, block: PercentageFieldBuilder.() -> Unit = {}) {
        fields += PercentageFieldBuilder(key).apply(block).build()
    }

    fun build() = IdeaCategorySchema(category, fields)
}

/**
 * DSL Entry Point
 */
fun ideaCategory(category: IdeaCategory, block: IdeaCategorySchemaBuilder.() -> Unit): IdeaCategorySchema {
    return IdeaCategorySchemaBuilder(category).apply(block).build()
}

