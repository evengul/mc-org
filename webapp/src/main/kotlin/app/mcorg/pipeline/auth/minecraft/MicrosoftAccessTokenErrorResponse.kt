package app.mcorg.pipeline.auth.minecraft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MicrosoftAccessTokenErrorResponse(@SerialName("error") val error: String,
                                             @SerialName("error_description") val description: String,
                                             @SerialName("error_codes") val errorCodes: List<Int>,
                                             @SerialName("timestamp") val timestamp: String,
                                             @SerialName("trace_id") val traceId: String,
                                             @SerialName("correlation_id") val correlationId: String,
                                             @SerialName("error_uri") val errorUri: String)