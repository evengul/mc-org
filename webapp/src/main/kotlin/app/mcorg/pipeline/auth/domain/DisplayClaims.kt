package app.mcorg.pipeline.auth.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DisplayClaims(@SerialName("xui") val xui: List<Uhs>)