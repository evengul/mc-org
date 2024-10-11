package app.mcorg.domain.categorization

import app.mcorg.domain.categorization.value.*

data class Common(val commonValues: MutableList<CategoryValue> = mutableListOf())

fun Common.boolean(id: String, name: String, init: BooleanValue.() -> Unit = {}) {
    val value = BooleanValue(id, name)
    value.init()
    commonValues.add(value)
}

fun <T : Enum<T>> Common.enum(id: String, name: String, init: EnumValue<T>.() -> Unit = {}) {
    val value = EnumValue<T>(id, name)
    value.init()
    commonValues.add(value)
}

fun Common.authors(init: AuthorValue.() -> Unit = {}) {
    val value = AuthorValue()
    value.init()
    commonValues.add(value)
}

fun Common.versions(init: VersionValue.() -> Unit = {}) {
    val value = VersionValue()
    value.init()
    commonValues.add(value)
}

fun Common.testResults(init: TestResultValue.() -> Unit = {}) {
    val value = TestResultValue()
    value.init()
    commonValues.add(value)
}
