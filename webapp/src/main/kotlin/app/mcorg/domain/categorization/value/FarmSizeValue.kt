package app.mcorg.domain.categorization.value

data class FarmSizeValue(var x: Int? = null, var y: Int? = null, var z: Int? = null) : CategoryValue {
    override val id: String
        get() = "farm.size"
    override val name: String
        get() = "Farm size"
}

fun FarmSizeValue.volume(): Int? {
    return x?.let { x1 -> y?.let { y1 -> z?.let { z1 -> x1 * y1 * z1 }}}
}