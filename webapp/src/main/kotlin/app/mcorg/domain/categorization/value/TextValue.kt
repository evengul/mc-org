package app.mcorg.domain.categorization.value

data class TextValue(override val id: String, override val name: String, var value: String? = null) : CategoryValue
