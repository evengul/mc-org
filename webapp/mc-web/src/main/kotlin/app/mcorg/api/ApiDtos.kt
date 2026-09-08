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

// ── Reporter: tags and contents (MCO-532) ──────────────────────────────────────

/** The items one project's reporter should bother reporting — its targets plus its plan items. */
@Serializable
data class ItemsOfInterestDto(
    @SerialName("project_id") val projectId: Int,
    @SerialName("item_ids") val itemIds: List<String>,
)

/**
 * What the sweep needs to do its job: which containers to read, and which items are worth
 * reporting from them.
 *
 * `items_of_interest` is what keeps the push bounded. Without it the API would receive an inventory
 * of somebody's junk drawer, and the webapp would quietly become a whole-world item census —
 * interesting, but MCO-526's business, not this one's.
 */
@Serializable
data class ReporterTagsResponse(
    @SerialName("world_id") val worldId: Int,
    @SerialName("containers") val containers: List<ContainerTagDto>,
    @SerialName("items_of_interest") val itemsOfInterest: List<ItemsOfInterestDto>,
)

@Serializable
data class ReportedItemDto(
    @SerialName("item_id") val itemId: String,
    @SerialName("count") val count: Long,
)

/**
 * One container as the sweep found it. [state] is `ok`, `unreadable` (the chunk has not been loaded
 * since tagging) or `missing` (the block is gone); only `ok` carries items worth storing.
 */
@Serializable
data class ReportedContainerDto(
    @SerialName("id") val id: Long,
    @SerialName("state") val state: String,
    @SerialName("seen_at") val seenAt: String,
    @SerialName("items") val items: List<ReportedItemDto> = emptyList(),
)

/**
 * A sweep's report. **Absolute for the containers it names**, and it names only those whose
 * contents changed since the last push; everything unnamed keeps what it had. One writer means
 * absolute is correct and self-healing — nothing to merge, no ordering hazard.
 *
 * [worldId] is optional for a reporter token, whose world is fixed by the token itself, and
 * required for a player token (the singleplayer path, where there is no server operator to hold a
 * reporter token). [reporterVersion] is stamped onto the token so world settings can say which
 * build is talking.
 */
@Serializable
data class ReporterContentsRequest(
    @SerialName("world_id") val worldId: Int? = null,
    @SerialName("swept_at") val sweptAt: String? = null,
    @SerialName("reporter_version") val reporterVersion: String? = null,
    @SerialName("containers") val containers: List<ReportedContainerDto> = emptyList(),
)

@Serializable
data class ReporterContentsResponse(
    @SerialName("accepted") val accepted: Int,
    /** Named containers that are not this world's. Reported back rather than silently ignored. */
    @SerialName("rejected") val rejected: Int,
    @SerialName("projects_recomputed") val projectsRecomputed: Int,
)

// ── The HUD's count poll (MCO-532) ─────────────────────────────────────────────

/**
 * One measured count. [measured] is evidence from tagged containers and is **not**
 * `ResourceDto.collected`, which stays the human's number — the two are kept apart on purpose, and
 * showing their disagreement is phase E's job.
 *
 * [oldestSeenAt] is the oldest contributing reading, which is what tells a player how much to trust
 * the number.
 */
@Serializable
data class WorldStorageDto(
    @SerialName("project_id") val projectId: Int,
    @SerialName("item_id") val itemId: String,
    @SerialName("measured") val measured: Long,
    @SerialName("container_count") val containerCount: Int,
    @SerialName("oldest_seen_at") val oldestSeenAt: String? = null,
)
