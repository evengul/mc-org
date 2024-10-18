package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class Mobs(override var value: MutableList<Value>? = null) : CategoryFilter<MutableList<Mobs.Value>> {
    override val id: String
        get() = "farm.required-mobs"
    override val name: String
        get() = "Required mobs"

    override fun validate(): Boolean {
        val copy = value ?: return super.validate()

        return copy.isEmpty() || copy.all { it.name.isNotBlank() && it.amount > 0 }
    }

    data class Value(val name: String, val amount: Int)
}