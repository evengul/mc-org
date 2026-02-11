package app.mcorg.domain.model.minecraft

import kotlinx.serialization.Serializable

@Serializable
sealed interface MinecraftId {
    val id: String
    val name: String
}