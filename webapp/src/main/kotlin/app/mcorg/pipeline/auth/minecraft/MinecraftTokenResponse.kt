package app.mcorg.pipeline.auth.minecraft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MinecraftTokenResponse(
    val username: String,
    val roles: List<String> = emptyList(),
    val metadata: EmptyClass = EmptyClass(),
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String
)