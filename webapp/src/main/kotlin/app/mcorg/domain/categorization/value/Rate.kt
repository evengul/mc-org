package app.mcorg.domain.categorization.value

data class RateModes(override val id: String, override val name: String, val rates: MutableList<RateMode> = mutableListOf()) : CategoryValue
data class RateMode(val name: String, val rates: Rates)
data class Rates(val rates: MutableList<Rate> = mutableListOf())
data class Rate(override val id: String, override val name: String, val amount: Int) : CategoryValue
