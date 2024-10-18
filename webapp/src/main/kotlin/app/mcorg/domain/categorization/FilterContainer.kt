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

fun FilterContainer.number(id: String, name: String, init: NumberFilter.() -> Unit = {}) {
    val value = NumberFilter(id, name)
    value.init()
    filter(value)
}

fun FilterContainer.text(id: String, name: String, init: TextFilter.() -> Unit = {}) {
    val value = TextFilter(id, name)
    value.init()
    filter(value)
}

fun FilterContainer.textList(id: String, name: String, init: AllowedList<String>.() -> Unit = {}) {
    val value = AllowedList<String>(id, name)
    value.init()
    filter(value)
}

fun <T : Enum<T>> FilterContainer.enum(id: String, name: String, init: EnumFilter<T>.() -> Unit = {}) {
    val value = EnumFilter<T>(id, name)
    value.init()
    filter(value)
}

fun <T : Enum<T>> FilterContainer.enumList(id: String, name: String, init: AllowedList<T>.() -> Unit = {}) {
    val value = AllowedList<T>(id, name)
    value.init()
    filter(value)
}