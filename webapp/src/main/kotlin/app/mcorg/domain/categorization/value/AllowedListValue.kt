package app.mcorg.domain.categorization.value

data class AllowedListValue<T>(override val id: String, override val name: String, var values: MutableList<T> = mutableListOf()) : CategoryValue
