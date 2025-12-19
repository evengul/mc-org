package app.mcorg.domain.model.minecraft

import kotlinx.serialization.Serializable

@Serializable
data class Item(
    val id: String,
    val name: String
)