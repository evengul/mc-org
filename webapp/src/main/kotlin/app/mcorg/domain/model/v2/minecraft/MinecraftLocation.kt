package app.mcorg.domain.model.v2.minecraft

data class MinecraftLocation(
    val dimension: Dimension,
    val x: Int,
    val y: Int,
    val z: Int,
)
