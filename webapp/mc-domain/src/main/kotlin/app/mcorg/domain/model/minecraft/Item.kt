package app.mcorg.domain.model.minecraft

import kotlinx.serialization.Serializable

@Serializable
data class Item(
    override val id: String,
    override val name: String
) : MinecraftId