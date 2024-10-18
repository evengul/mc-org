package app.mcorg.domain.categorization

import app.mcorg.domain.categorization.subtypes.SubCategoryType
import app.mcorg.domain.categorization.value.*

/**
 * Categorization: Used to categorize contraptions
 *
 * Example: A slime farm is a
 *  - mob (subcategory)
 *  - farm (category)
 *  with
 *   - requirements/consumption (amount, amount/hr)
 *   - rates (amount/hr)
 *  It should be built at Low y-level (requirement)
 *  It has a size (three-dimensional value)
 *  It is stackable (Boolean), Tileable (Boolean)
 *  It can be built in ANY biome (ENUM or String)
 *  It might require iron golems (amount of mobs, requirement)
 *  It might require player setup with enchantments, and a beacon
 *  How to set up, How to use (text fields)
 *  It might be directional/locational (Boolean)
 *
 *  This means we have CategoryValue-s with many different types,
 *  all of which can be used to filter out the relevant contraptions the user might want.
 *
 *  The system uses a DSL to create the relevant categories and subcategories
 */
@CategoryMarker
open class Category(
    val type: CategoryType,
    val subCategories: MutableList<SubCategory> = mutableListOf(),
) : FilterContainer() {
    override fun toString(): String {
        return "Category(type=$type, filters=$filters, subCategories=$subCategories)"
    }
}

fun Category.subCategory(subCategoryType: SubCategoryType, init: SubCategory.() -> Unit = {}) {
    val subCategory = SubCategory(subCategoryType)
    subCategory.init()
    subCategories.add(subCategory)
}
