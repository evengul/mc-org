package app.mcorg.pipeline.world.settings

import app.mcorg.domain.model.minecraft.MinecraftVersion

data class UpdateWorldVersionInput(
    val version: MinecraftVersion
)
