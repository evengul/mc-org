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

// ── Is anything reading these containers? (MCO-536) ────────────────────────────

/**
 * The state of this world's reporter, for the player standing in front of a chest.
 *
 * Tagging a chest and watching nothing happen is the feature's worst failure, and its two causes
 * are indistinguishable from inside the game: nobody has connected a server, or a server is
 * connected to a *different* Seam world. Both leave tags piling up and numbers never moving. World
 * settings already answers this for whoever configures the server; this answers it for whoever is
 * holding the shulker box, who is usually not the same person and is definitely not holding a
 * browser.
 *
 * The three states are deliberately separate, because they have different fixes:
 * - `configured = false` — nobody has minted a token. Mint one in world settings.
 * - `configured = true, connected = false` — a token exists but no server has ever used it. It is
 *   not in a server's config, or the server has not run `/seam connect`.
 * - `connected = true` — a reporter has pushed. [lastSeenAt] says when, which is the only way to
 *   tell a live reporter from one that stopped an hour ago.
 *
 * [lastSeenAt] is the **token's** last use, not a container's last reading — deliberately. A
 * container in an unloaded chunk is not re-read, so per-container timestamps go stale while the
 * reporter is perfectly healthy; the empty heartbeat that stamps this one goes out on cadence
 * regardless of what is loaded. Deriving liveness from container readings would announce that
 * nothing is watching every time a player walks away from their storage room.
 */
@Serializable
data class ReporterStatusDto(
    @SerialName("configured") val configured: Boolean,
    @SerialName("connected") val connected: Boolean,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("reporter_version") val reporterVersion: String? = null,
    @SerialName("server_name") val serverName: String? = null,
    /** Live tokens for this world. More than one means more than one server may be reporting. */
    @SerialName("server_count") val serverCount: Int = 0,
)
