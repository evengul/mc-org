package app.mcorg.domain.model.resources

import app.mcorg.domain.model.minecraft.MinecraftLocation

data class ResourceLocation(
    val id: Int,
    val resource: String,
    val description: String,
    val biome: String,
    val location: MinecraftLocation
)
