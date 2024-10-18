package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class TextFilter(override val id: String, override val name: String, override var value: String? = null) : CategoryFilter<String>
