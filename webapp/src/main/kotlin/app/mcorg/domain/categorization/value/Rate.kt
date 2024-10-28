package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class RateModes(override val id: String, override val name: String, override var value: MutableList<RateMode>? = null) : CategoryFilter<MutableList<RateMode>> {
    override fun validate(): Boolean {
        val copy = value ?: return super.validate()

        return copy.isEmpty() || copy.all {
            it.name.isNotBlank() &&
                    it.rates.isNotEmpty() &&
                    it.rates.all { rate -> rate.second > -1 } }
    }
}

data class RateMode(val name: String, val rates: MutableList<Pair<String, Int>> = mutableListOf())