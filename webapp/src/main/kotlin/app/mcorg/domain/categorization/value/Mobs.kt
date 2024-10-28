package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class Mobs(val allowedValues: List<String>, override var value: MutableMap<String, Int>? = null) : CategoryFilter<MutableMap<String, Int>> {
    override val id: String
        get() = "farm.required-mobs"
    override val name: String
        get() = "Required mobs"

    override fun validate(): Boolean {
        val copy = value ?: return super.validate()

        return copy.isEmpty() || copy.all { it.key.isNotBlank() && it.value > 0 && allowedValues.contains(it.key) }
    }
}