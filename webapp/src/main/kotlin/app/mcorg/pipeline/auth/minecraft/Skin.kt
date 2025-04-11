package app.mcorg.pipeline.auth.minecraft

import kotlinx.serialization.Serializable

@Serializable
data class Skin(val id: String, val state: String, val url: String, val textureKey: String, val variant: String)