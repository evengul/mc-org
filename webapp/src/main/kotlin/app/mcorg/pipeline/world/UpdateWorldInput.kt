package app.mcorg.pipeline.world

import app.mcorg.domain.model.minecraft.MinecraftVersion

data class UpdateWorldInput(
    val name: String,
    val description: String,
    val version: MinecraftVersion
)
