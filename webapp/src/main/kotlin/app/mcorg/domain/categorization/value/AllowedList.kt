package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class AllowedList<T>(
    override val id: String,
    override val name: String,
    override var value: MutableList<T>? = null
) : CategoryFilter<MutableList<T>>
