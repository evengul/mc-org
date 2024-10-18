package app.mcorg.domain.categorization.value

import app.mcorg.domain.categorization.CategoryMarker

@CategoryMarker
data class FarmSize(override var value: Value? = null) : CategoryFilter<FarmSize.Value> {
    override val id: String
        get() = "farm.size"
    override val name: String
        get() = "Farm size"

    override fun validate(): Boolean {
        val copy = value ?: return super.validate()

        return copy.x > 0 && copy.y > 0 && copy.z > 0
    }

    data class Value(val x: Int, val y: Int, val z: Int)
}

fun FarmSize.Value.volume(): Int {
    return x * y * z
}