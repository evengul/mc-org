package app.mcorg.domain.model.resources

import app.mcorg.domain.model.minecraft.MinecraftLocation

data class ResourceProducer(
    val id: Int,
    val name: String,
    val location: MinecraftLocation,
    val resourceRates: Map<String, Int>
)
