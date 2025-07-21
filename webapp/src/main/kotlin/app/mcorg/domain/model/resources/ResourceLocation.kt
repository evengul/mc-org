package app.mcorg.domain.model.v2.resources

import app.mcorg.domain.model.v2.minecraft.MinecraftLocation

data class ResourceLocation(
    val id: Int,
    val resource: String,
    val description: String,
    val biome: String,
    val location: MinecraftLocation
)
