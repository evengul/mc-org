package app.mcorg.pipeline.auth.domain

import app.mcorg.logging.redacted

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MicrosoftAccessTokenResponse(@SerialName("token_type") val tokenType: String,
                                        @SerialName("scope") val scope: String,
                                        @SerialName("expires_in") val expiresIn: Int,
                                        @SerialName("ext_expires_in") val extExpiresIn: Int,
                                        @SerialName("access_token") val accessToken: String,
                                        @SerialName("id_token") val idToken: String) {
    // MCO-340: the generated toString() would print both tokens verbatim.
    override fun toString() = "MicrosoftAccessTokenResponse(tokenType=$tokenType, scope=$scope, " +
        "expiresIn=$expiresIn, extExpiresIn=$extExpiresIn, " +
        "accessToken=${redacted(accessToken)}, idToken=${redacted(idToken)})"
}