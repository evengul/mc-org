package app.mcorg.domain.model.idea.schema

import app.mcorg.domain.model.idea.IdeaCategory

/**
 * Represents the complete schema for an idea category.
 * Contains all field definitions for category-specific data.
 */
data class IdeaCategorySchema(
    val category: IdeaCategory,
    val fields: List<CategoryField>,
    val subcategories: Map<String, List<CategoryField>> = emptyMap()
) {
    fun getField(key: String): CategoryField? = fields.find { it.key == key }

    fun getSearchableFields(): List<CategoryField> = fields.filter { it.searchable }

    fun getFilterableFields(): List<CategoryField> = fields.filter { it.filterable }

    fun getRequiredFields(): List<CategoryField> = fields.filter { it.required }

    fun getSubcategoryFields(subcategory: String): List<CategoryField>? = subcategories[subcategory]

    fun getAllFields(): List<CategoryField> = fields + subcategories.values.flatten()
}

