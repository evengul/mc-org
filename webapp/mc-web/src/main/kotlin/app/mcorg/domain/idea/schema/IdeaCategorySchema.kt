package app.mcorg.domain.model.idea.schema

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.minecraft.MinecraftVersionRange

/**
 * Represents the complete schema for an idea category.
 * Contains all field definitions for category-specific data.
 */
data class IdeaCategorySchema(
    val category: IdeaCategory,
    val fields: List<CategoryField>,

    var versionRange: MinecraftVersionRange = MinecraftVersionRange.Unbounded
) {
    fun getField(key: String): CategoryField? = fields.find { it.key == key }

    fun getFilterableFields(): List<CategoryField> = fields.filter { it.filterable }

    fun getRequiredFields(): List<CategoryField> = fields.filter { it.required }
}
