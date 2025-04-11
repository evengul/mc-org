package app.mcorg.pipeline.auth.minecraft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XboxTokenResponse(
    @SerialName("IssueInstant") val issueInstant: String,
    @SerialName("NotAfter") val notAfter: String,
    @SerialName("Token") val token: String,
    @SerialName("DisplayClaims") val displayClaims: DisplayClaims
) {
    fun userHash(): String {
        return this.displayClaims.xui[0].uhs
    }
}