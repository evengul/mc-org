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
    /**
     * Which client last set [collected]: `manual` (web app) or `mod` (this API). Distinct from
     * [sourceType], which is the item's acquisition type from the graph.
     */
    @SerialName("progress_source") val progressSource: String = "manual",
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

// ── Gathering plan (MCO-533) ───────────────────────────────────────────────────

/**
 * One node of a project's gathering plan — the raw-gather / smelt / craft work expanded from its
 * requirements, as opposed to the `resource_gathering` **target** items [ProjectDto] carries.
 *
 * That distinction is the point of the endpoint: a project asks for 64 hoppers, and the plan says
 * go mine iron and chop wood. Target mode answers "what does the build need", this answers "what am
 * I doing this afternoon".
 *
 * [quantity] is a `Long` in the engine and stays one on the wire — the mod formats it with
 * thousands separators, and clamping to Int here would silently corrupt a large plan.
 *
 * [activityGroup] is `ActivityGroup` (`GATHER`, `SMELT`, `CRAFT`, `NEEDS_ATTENTION`, …) and
 * [status] is `PlanNodeStatus` (`RESOLVED`, `RAW_GATHER`, `SUPPLIED`, `OPEN_TAG`, `BLOCKED`), both
 * as their enum names. A plan full of `NEEDS_ATTENTION` is a normal answer, not an error.
 */
@Serializable
data class PlanActivityDto(
    @SerialName("item_id") val itemId: String,
    @SerialName("name") val name: String,
    @SerialName("quantity") val quantity: Long,
    @SerialName("activity_group") val activityGroup: String,
    @SerialName("status") val status: String,
)

// ── Container tags (MCO-530) ───────────────────────────────────────────────────

/**
 * A tagged container. [groupKey] is shared by both halves of a double chest (the position itself
 * otherwise) — the sweep dedupes on it so a joined chest is not counted twice.
 *
 * [lastSeenAt] and [state] are written only by the reporter (phase B): `unreadable` until a sweep
 * has read the position, then `ok`, or `missing` once the block is gone. Timestamps are ISO-8601.
 */
@Serializable
data class ContainerTagDto(
    @SerialName("id") val id: Long,
    @SerialName("project_id") val projectId: Int,
    @SerialName("dimension") val dimension: String,
    @SerialName("x") val x: Int,
    @SerialName("y") val y: Int,
    @SerialName("z") val z: Int,
    @SerialName("group_key") val groupKey: String,
    @SerialName("kind") val kind: String,
    @SerialName("tagged_by") val taggedBy: String? = null,
    @SerialName("tagged_at") val taggedAt: String,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("state") val state: String,
)

/** Tag a position, or move an existing tag there to another project. [groupKey] defaults to the position. */
@Serializable
data class ContainerTagRequest(
    @SerialName("dimension") val dimension: String,
    @SerialName("x") val x: Int,
    @SerialName("y") val y: Int,
    @SerialName("z") val z: Int,
    @SerialName("project_id") val projectId: Int,
    @SerialName("kind") val kind: String,
    @SerialName("group_key") val groupKey: String? = null,
)

@Serializable
data class ContainerTagsResponse(
    @SerialName("containers") val containers: List<ContainerTagDto>,
)
