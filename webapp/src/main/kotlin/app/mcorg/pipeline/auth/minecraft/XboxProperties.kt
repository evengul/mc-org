package app.mcorg.pipeline.auth.minecraft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XboxProperties(@SerialName("AuthMethod") val authMethod: String,
                          @SerialName("SiteName") val siteName: String,
                          @SerialName("RpsTicket") val rpsTicket: String)