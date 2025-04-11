package app.mcorg.pipeline.auth.minecraft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XstsRequest(@SerialName("Properties") val properties: XstsProperties,
                       @SerialName("RelyingParty") val relyingParty: String,
                       @SerialName("TokenType") val tokenType: String)