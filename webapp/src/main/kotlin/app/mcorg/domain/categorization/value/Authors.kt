package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class Credits(
    override var value: MutableMap<String, String>? = null
) : CategoryFilter<MutableMap<String, String>> {
    override val id: String
        get() = "common.credits"
    override val name: String
        get() = "Credits"

    override fun validate(value: MutableMap<String, String>?): Boolean {
        return !value.isNullOrEmpty() && value.all { it.key.isNotBlank() && it.value.isNotBlank() }
    }
}

@CategoryMarker
data class Authors(override var value: MutableList<String>? = null) : CategoryFilter<MutableList<String>> {
    override val id: String
        get() = "common.authors"
    override val name: String
        get() = "Authors"

    override fun validate(value: MutableList<String>?): Boolean {
        return !value.isNullOrEmpty() && value.all { it.isNotBlank() }
    }
}

fun Authors.author(author: String) {
    value?.add(author) ?: {
        value = mutableListOf(author)
    }
}

fun Credits.credit(name: String, credited: String) {
    value?.put(name, credited) ?: {
        value = mutableMapOf(name to credited)
    }
}
