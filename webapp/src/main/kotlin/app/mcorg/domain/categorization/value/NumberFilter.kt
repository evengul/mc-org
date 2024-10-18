package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class NumberFilter(override val id: String, override val name: String, override var value: Number? = null) : CategoryFilter<Number>
