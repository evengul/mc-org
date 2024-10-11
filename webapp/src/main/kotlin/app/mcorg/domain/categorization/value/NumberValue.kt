package app.mcorg.domain.categorization.value

data class NumberValue(override val id: String, override val name: String, var value: Number? = null) : CategoryValue
