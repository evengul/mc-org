package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class IntFilter(
    override val id: String,
    override val name: String,
    override var value: Int? = null,
    var min: Int? = null,
    var max: Int? = null,
) : CategoryFilter<Int> {
    override fun validate(): Boolean {
        val copy = value ?: return super.validate()
        if (min == null && max == null) return super.validate()
        if (min != null && copy < (min ?: Int.MAX_VALUE)) return false
        if (max != null && copy > (max ?: Int.MIN_VALUE)) return false
        return true
    }
}

@CategoryMarker
data class DoubleFilter(
    override val id: String,
    override val name: String,
    override var value: Double? = null,
    var min: Double? = null,
    var max: Double? = null,
) : CategoryFilter<Double> {
    override fun validate(): Boolean {
        val copy = value ?: return super.validate()
        if (min == null && max == null) return super.validate()
        if (min != null && copy < (min ?: Double.MAX_VALUE)) return false
        if (max != null && copy > (max ?: Double.MIN_VALUE)) return false
        return true
    }
}
