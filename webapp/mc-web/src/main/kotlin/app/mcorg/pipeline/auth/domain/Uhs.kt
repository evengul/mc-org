package app.mcorg.pipeline.auth.domain

import app.mcorg.logging.redacted

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Uhs(@SerialName("uhs") val uhs: String) {
    // MCO-340: the Xbox user hash is sent as part of the XSTS/Minecraft auth header.
    override fun toString() = "Uhs(uhs=${redacted(uhs)})"
}