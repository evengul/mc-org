package app.mcorg.infrastructure.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MicrosoftAccessTokenResponse(@SerialName("token_type") val tokenType: String,
                                        @SerialName("scope") val scope: String,
                                        @SerialName("expires_in") val expiresIn: Int,
                                        @SerialName("ext_expires_in") val extExpiresIn: Int,
                                        @SerialName("access_token") val accessToken: String,
                                        @SerialName("id_token") val idToken: String)

data class MinecraftProfileNotFoundResponse(
    val path: String,
    val errorType: String,
    val error: String,
    val errorMessage: String,
    val developerMessage: String
)

@Serializable
data class MinecraftProfileResponse(
    val id: String,
    val name: String
)

data class MinecraftRequest(
    val identityToken: String
)

fun createMinecraftRequest(userHash: String, xstsToken: String) = "XBL3.0 x=$userHash;$xstsToken"

@Serializable
data class MinecraftTokenResponse(
    val username: String,
    @SerialName("access_token") val accessToken: String
)

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

@Serializable
data class DisplayClaims(@SerialName("xui") val xui: List<Uhs>)
@Serializable
data class Uhs(@SerialName("uhs") val uhs: String)

@Serializable
data class XstsRequest(@SerialName("Properties") val properties: XstsProperties,
                       @SerialName("RelyingParty") val relyingParty: String,
                       @SerialName("TokenType") val tokenType: String)

@Serializable
data class XstsProperties(@SerialName("SandboxId") val sandboxId: String,
                          @SerialName("UserTokens") val userTokens: List<String>)

@Serializable
data class XboxProfileRequest(
    @SerialName("Properties") val properties: XboxProperties,
    @SerialName("RelyingParty") val relyingParty: String,
    @SerialName("TokenType") val tokenType: String
)

@Serializable
data class XboxProperties(@SerialName("AuthMethod") val authMethod: String,
                          @SerialName("SiteName") val siteName: String,
                          @SerialName("RpsTicket") val rpsTicket: String)