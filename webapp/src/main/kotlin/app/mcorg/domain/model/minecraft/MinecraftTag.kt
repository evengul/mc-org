package app.mcorg.domain.model.minecraft

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftTag(
    override val id: String,
    override val name: String,
    val content: List<Item>
) : MinecraftId
