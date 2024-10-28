package app.mcorg.domain.categorization

import app.mcorg.domain.categorization.value.*

sealed class FilterContainer(val filters: MutableList<CategoryFilter<*>> = mutableListOf()) {
    fun filter(filter: CategoryFilter<*>) {
        filters.add(filter)
    }
}

fun FilterContainer.boolean(id: String, name: String, init: BooleanFilter.() -> Unit = {}) {
    val value = BooleanFilter(id, name)
    value.init()
    filter(value)
}

fun FilterContainer.integer(id: String, name: String, init: IntFilter.() -> Unit = {}) {
    val value = IntFilter(id, name)
    value.init()
    filter(value)
}

fun FilterContainer.nonNegativeInteger(id: String, name: String, init: IntFilter.() -> Unit = {}) {
    val value = IntFilter(id, name)
    value.min = 0
    value.init()
    filter(value)
}

fun FilterContainer.double(id: String, name: String, init: DoubleFilter.() -> Unit = {}) {
    val value = DoubleFilter(id, name)
    value.init()
    filter(value)
}

fun FilterContainer.nonNegativeDouble(id: String, name: String, init: DoubleFilter.() -> Unit = {}) {
    val value = DoubleFilter(id, name)
    value.min = 0.0
    value.init()
    filter(value)
}

fun FilterContainer.percentage(id: String, name: String, init: DoubleFilter.() -> Unit = {}) {
    val value = DoubleFilter(id, name)
    value.min = 0.0
    value.max = 1.0
    value.init()
    filter(value)
}

fun FilterContainer.text(id: String, name: String, canBeFiltered: Boolean = true, longText: Boolean = false, init: TextFilter.() -> Unit = {}) {
    val value = TextFilter(id, name, canBeFiltered = canBeFiltered, longText = longText)
    value.init()
    filter(value)
}

fun FilterContainer.textList(id: String, name: String, allowedValues: List<String>, init: AllowedList<String>.() -> Unit = {}) {
    val value = AllowedList(id, name, allowedValues)
    value.init()
    filter(value)
}

fun <T : Enum<T>> FilterContainer.enum(id: String, name: String, clazz: Class<T>, init: EnumFilter<T>.() -> Unit = {}) {
    val value = EnumFilter(id, name, clazz)
    value.init()
    filter(value)
}

fun <T : Enum<T>> FilterContainer.enumList(id: String, name: String, clazz: Class<T>, init: AllowedList<T>.() -> Unit = {}) {
    val value = AllowedList(id, name, clazz.enumConstants.toList())
    value.init()
    filter(value)
}