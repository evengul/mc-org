package app.mcorg.domain.model.idea

/**
 * Represents 3D dimensions for contraptions.
 * Used for farms, storage systems, and other builds.
 */
data class Dimensions(
    val x: Int,
    val y: Int,
    val z: Int
) {
    init {
        require(x > 0) { "X dimension must be positive" }
        require(y > 0) { "Y dimension must be positive" }
        require(z > 0) { "Z dimension must be positive" }
    }

    override fun toString(): String = "$x × $y × $z"

    val volume: Int get() = x * y * z
}

