package app.mcorg.domain.model.minecraft

sealed interface MinecraftId {
    val id: String
    val name: String
}