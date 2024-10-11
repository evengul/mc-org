package app.mcorg.domain.categorization

import app.mcorg.domain.categorization.subtypes.SubCategoryType
import app.mcorg.domain.categorization.value.*

data class SubCategory(
    val type: SubCategoryType,
    val values: MutableList<CategoryValue> = mutableListOf()
)

fun SubCategory.value(value: CategoryValue) = values.add(value)

fun SubCategory.boolean(id: String, name: String, init: BooleanValue.() -> Unit = {}) {
    val value = BooleanValue(id, name)
    value.init()
    values.add(value)
}

fun SubCategory.number(id: String, name: String, init: NumberValue.() -> Unit = {}) {
    val value = NumberValue(id, name)
    value.init()
    values.add(value)
}

fun SubCategory.text(id: String, name: String, init: TextValue.() -> Unit = {}) {
    val value = TextValue(id, name)
    value.init()
    values.add(value)
}

fun SubCategory.textList(id: String, name: String, init: AllowedListValue<String>.() -> Unit = {}) {
    val value = AllowedListValue<String>(id, name)
    value.init()
    values.add(value)
}

fun <T : Enum<T>> SubCategory.enumList(id: String, name: String, init: AllowedListValue<T>.() -> Unit = {}) {
    val value = AllowedListValue<T>(id, name)
    value.init()
    values.add(value)
}
