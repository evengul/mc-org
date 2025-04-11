package app.mcorg.pipeline.auth.minecraft

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftProfileResponse(
    val id: String,
    val name: String,
    val skins: List<Skin>,
    val capes: List<Cape>,
    val profileActions: EmptyClass
)