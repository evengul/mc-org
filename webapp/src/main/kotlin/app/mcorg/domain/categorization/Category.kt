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
open class Category(
    val type: CategoryType,
    val values: MutableList<CategoryValue> = mutableListOf(),
    val subCategories: MutableList<SubCategory> = mutableListOf(),
) {
    override fun toString(): String {
        return "Category(type=$type, values=$values, subCategories=$subCategories)"
    }
}

fun Category.subCategory(subCategoryType: SubCategoryType, init: SubCategory.() -> Unit = {}) {
    val subCategory = SubCategory(subCategoryType)
    subCategory.init()
    subCategories.add(subCategory)
}

fun Category.value(value: CategoryValue) = values.add(value)

fun Category.boolean(id: String, name: String, init: BooleanValue.() -> Unit = {}) {
    val value = BooleanValue(id, name)
    value.init()
    values.add(value)
}

fun Category.number(id: String, name: String, init: NumberValue.() -> Unit = {}) {
    val value = NumberValue(id, name)
    value.init()
    values.add(value)
}

fun Category.text(id: String, name: String, init: TextValue.() -> Unit = {}) {
    val value = TextValue(id, name)
    value.init()
    values.add(value)
}

fun Category.textList(id: String, name: String, init: AllowedListValue<String>.() -> Unit = {}) {
    val value = AllowedListValue<String>(id, name)
    value.init()
    values.add(value)
}

fun <T : Enum<T>> Category.enumList(id: String, name: String, init: AllowedListValue<T>.() -> Unit = {}) {
    val value = AllowedListValue<T>(id, name)
    value.init()
    values.add(value)
}
