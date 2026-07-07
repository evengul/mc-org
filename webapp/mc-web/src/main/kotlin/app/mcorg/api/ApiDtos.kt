package app.mcorg.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the mod-facing JSON API (MCO-235 / MCO-236). All fields are snake_case on the wire
 * (via [SerialName]) — this is the stable contract the Seam Companion mod client is built against.
 */

// ── Auth: device-code flow ─────────────────────────────────────────────────────

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("interval") val interval: Int,
)

@Serializable
data class PollRequest(
    @SerialName("device_code") val deviceCode: String,
)

/** RFC-8628-style pending/error body: `authorization_pending`, `slow_down`, `expired_token`, `access_denied`. */
@Serializable
data class PollPendingResponse(
    @SerialName("error") val error: String,
)

@Serializable
data class PollSuccessResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("username") val username: String,
)

// ── Generic error ──────────────────────────────────────────────────────────────

@Serializable
data class ApiErrorResponse(
    @SerialName("error") val error: String,
    @SerialName("message") val message: String? = null,
)

@Serializable
data class OkResponse(
    @SerialName("ok") val ok: Boolean = true,
)

// ── Read/write resources ───────────────────────────────────────────────────────

@Serializable
data class WorldDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("version") val version: String,
    @SerialName("total_projects") val totalProjects: Int,
    @SerialName("completed_projects") val completedProjects: Int,
)

@Serializable
data class ResourceDto(
    @SerialName("item_id") val itemId: String,
    @SerialName("name") val name: String,
    @SerialName("required") val required: Int,
    @SerialName("collected") val collected: Int,
    @SerialName("source_type") val sourceType: String? = null,
)

@Serializable
data class TaskDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("completed") val completed: Boolean,
)

@Serializable
data class ProjectDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("stage") val stage: String,
    @SerialName("state") val state: String,
    @SerialName("resources") val resources: List<ResourceDto>,
    @SerialName("tasks") val tasks: List<TaskDto>,
)

// ── Sync (absolute set) ─────────────────────────────────────────────────────────

@Serializable
data class SyncResourceItem(
    @SerialName("item_id") val itemId: String,
    @SerialName("collected") val collected: Int,
)

@Serializable
data class SyncRequest(
    @SerialName("resources") val resources: List<SyncResourceItem>,
)

@Serializable
data class ResourcesResponse(
    @SerialName("resources") val resources: List<ResourceDto>,
)

@Serializable
data class TaskUpdateRequest(
    @SerialName("completed") val completed: Boolean,
)
