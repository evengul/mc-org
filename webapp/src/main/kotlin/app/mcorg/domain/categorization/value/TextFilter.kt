package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class TextFilter(
    override val id: String,
    override val name: String,
    val longText: Boolean = false,
    override val canBeFiltered: Boolean = true,
    override var value: String? = null
) : CategoryFilter<String>
