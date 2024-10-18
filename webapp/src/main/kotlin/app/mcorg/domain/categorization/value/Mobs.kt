package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class Mobs(override var value: MutableList<Value>? = null) : CategoryFilter<MutableList<Mobs.Value>> {
    override val id: String
        get() = "farm.required-mobs"
    override val name: String
        get() = "Required mobs"

    data class Value(val name: String, val amount: Int)
}