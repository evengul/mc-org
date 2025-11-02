package app.mcorg.pipeline.auth.domain

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftProfileResponse(
    val id: String,
    val name: String
)