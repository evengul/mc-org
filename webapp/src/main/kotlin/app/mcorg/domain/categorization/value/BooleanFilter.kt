package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class BooleanFilter(
    override val id: String,
    override val name: String,
    override var value: Boolean? = null
) : CategoryFilter<Boolean>
