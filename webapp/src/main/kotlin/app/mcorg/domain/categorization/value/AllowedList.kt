package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class AllowedList<T>(
    override val id: String,
    override val name: String,
    val allowedValues: List<T>,
    override var value: MutableList<T>? = null
) : CategoryFilter<MutableList<T>>
