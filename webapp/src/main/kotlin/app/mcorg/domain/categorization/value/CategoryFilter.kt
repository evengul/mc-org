package app.mcorg.domain.categorization.value

interface CategoryFilter<T> {
    val id: String
    val name: String

    val canBeFiltered: Boolean
        get() = true

    var value: T?

    fun validate(): Boolean {
        return true
    }
}