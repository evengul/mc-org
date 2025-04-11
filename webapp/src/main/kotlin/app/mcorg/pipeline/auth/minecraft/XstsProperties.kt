package app.mcorg.pipeline.auth.minecraft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XstsProperties(@SerialName("SandboxId") val sandboxId: String,
                          @SerialName("UserTokens") val userTokens: List<String>)