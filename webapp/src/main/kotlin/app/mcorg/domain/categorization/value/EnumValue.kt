package app.mcorg.domain.categorization.value

data class EnumValue<T : Enum<T>>(override val id: String, override val name: String, var value: T? = null) : CategoryValue
