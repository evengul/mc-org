package app.mcorg.domain.model.minecraft

import app.mcorg.domain.model.resources.ResourceSource

data class ServerData(
    val version: MinecraftVersion.Release,
    val items: List<MinecraftId>,
    val sources: List<ResourceSource>
)

