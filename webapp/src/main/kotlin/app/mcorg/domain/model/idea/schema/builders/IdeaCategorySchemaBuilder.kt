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
    private val subcategories = mutableMapOf<String, MutableList<CategoryField>>()

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

    fun rateField(key: String, block: RateFieldBuilder.() -> Unit = {}) {
        fields += RateFieldBuilder(key).apply(block).build()
    }

    fun mapField(key: String, block: MapFieldBuilder.() -> Unit = {}) {
        fields += MapFieldBuilder(key).apply(block).build()
    }

    fun listField(key: String, block: ListFieldBuilder.() -> Unit = {}) {
        fields += ListFieldBuilder(key).apply(block).build()
    }

    fun percentageField(key: String, block: PercentageFieldBuilder.() -> Unit = {}) {
        fields += PercentageFieldBuilder(key).apply(block).build()
    }

    /**
     * Define a subcategory with its own fields
     */
    fun subcategory(name: String, block: SubcategoryBuilder.() -> Unit) {
        val builder = SubcategoryBuilder(name)
        builder.apply(block)
        subcategories[name] = builder.fields
    }

    fun build() = IdeaCategorySchema(category, fields, subcategories)
}

/**
 * Builder for subcategories within a category
 */
class SubcategoryBuilder(val name: String) {
    internal val fields = mutableListOf<CategoryField>()

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

    fun rateField(key: String, block: RateFieldBuilder.() -> Unit = {}) {
        fields += RateFieldBuilder(key).apply(block).build()
    }
}

/**
 * DSL Entry Point
 */
fun ideaCategory(category: IdeaCategory, block: IdeaCategorySchemaBuilder.() -> Unit): IdeaCategorySchema {
    return IdeaCategorySchemaBuilder(category).apply(block).build()
}

