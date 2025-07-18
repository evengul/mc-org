package app.mcorg.domain.model.v2.resources

import app.mcorg.domain.model.v2.minecraft.MinecraftLocation

data class ResourceProducer(
    val id: Int,
    val name: String,
    val location: MinecraftLocation,
    val resourceRates: Map<String, Int>
)
