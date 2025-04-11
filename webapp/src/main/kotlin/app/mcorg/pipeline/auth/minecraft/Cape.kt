package app.mcorg.pipeline.auth.minecraft

import kotlinx.serialization.Serializable

@Serializable
data class Cape(val id: String, val state: String, val url: String, val alias: String)