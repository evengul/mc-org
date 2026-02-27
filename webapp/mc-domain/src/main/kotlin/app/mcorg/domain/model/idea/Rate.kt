package app.mcorg.domain.model.idea

/**
 * Represents a production or consumption rate.
 * Commonly used for farms and storage systems.
 */
data class Rate(
    val amount: Double,
    val unit: String = "items/hour"
) {
    init {
        require(amount >= 0.0) { "Rate amount must be non-negative" }
    }

    override fun toString(): String = "$amount $unit"
}

