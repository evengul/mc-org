package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class RateModes(override val id: String, override val name: String, override var value: MutableList<RateMode>? = null) : CategoryFilter<MutableList<RateMode>>
data class RateMode(val name: String, val rates: Rates)
data class Rates(val rates: MutableList<NumberFilter> = mutableListOf())