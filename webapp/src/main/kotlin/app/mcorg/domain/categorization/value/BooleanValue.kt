package app.mcorg.domain.categorization.value

data class BooleanValue(override val id: String, override val name: String, var value: Boolean? = null) : CategoryValue
