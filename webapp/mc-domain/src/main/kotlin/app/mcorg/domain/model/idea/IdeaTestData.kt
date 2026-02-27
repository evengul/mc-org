package app.mcorg.domain.model.idea

import app.mcorg.domain.model.minecraft.MinecraftVersion

data class IdeaTestData(
    val id: Int,
    val ideaId: Int,
    val mspt: Int,
    val version: MinecraftVersion
)
