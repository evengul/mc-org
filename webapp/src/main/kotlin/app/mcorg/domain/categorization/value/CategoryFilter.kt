package app.mcorg.domain.categorization.value

interface CategoryFilter<T> {
    val id: String
    val name: String

    var value: T?

    fun validate(value: T?): Boolean {
        return true
    }
}