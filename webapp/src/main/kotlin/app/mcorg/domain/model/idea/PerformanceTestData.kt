package app.mcorg.domain.model.idea

import app.mcorg.domain.model.minecraft.MinecraftVersion

/**
 * Performance test data for an idea.
 * Captures MSPT (milliseconds per tick) performance metrics.
 */
data class PerformanceTestData(
    val mspt: Double,
    val hardware: String,
    val version: MinecraftVersion
) {
    init {
        require(mspt >= 0.0) { "MSPT must be non-negative" }
    }
}

