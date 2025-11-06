package app.mcorg.pipeline.auth.domain

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftRequest(
    val identityToken: String
)

fun createMinecraftRequest(userHash: String, xstsToken: String) = "XBL3.0 x=$userHash;$xstsToken"