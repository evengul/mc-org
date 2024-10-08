package app.mcorg.infrastructure.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModrinthVersion(
    val version: String,
    @SerialName("version_type")
    val versionType: String,
    val date: String,
    val major: Boolean
)