package app.mcorg.pipeline.auth.domain

import app.mcorg.logging.redacted

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

    // MCO-340: Token is the Xbox Live auth token; the user hash inside displayClaims identifies
    // the account and is used as a credential downstream, so neither is printable.
    override fun toString() = "XboxTokenResponse(issueInstant=$issueInstant, notAfter=$notAfter, " +
        "token=${redacted(token)}, displayClaims=$displayClaims)"
}