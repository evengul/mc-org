package app.mcorg.pipeline.auth.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Uhs(@SerialName("uhs") val uhs: String)