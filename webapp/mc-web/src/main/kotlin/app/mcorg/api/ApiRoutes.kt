package app.mcorg.api

import app.mcorg.config.AppConfig
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.event.ResourceCountUpdated
import app.mcorg.event.eventBus
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.project.commonsteps.GetProjectListStep
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.pipeline.resources.commonsteps.SetProgressByItemInput
import app.mcorg.pipeline.resources.commonsteps.SetProgressByItemStep
import app.mcorg.pipeline.task.commonsteps.GetActionTaskStep
import app.mcorg.pipeline.task.commonsteps.GetActionTasksForProjectStep
import app.mcorg.pipeline.task.commonsteps.SetTaskCompletedInput
import app.mcorg.pipeline.task.commonsteps.SetTaskCompletedStep
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsInput
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsStep
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.time.Duration
import java.time.Instant

/**
 * Mod-facing JSON API (MCO-235 / MCO-236). Mounted as a top-level machine surface — a sibling of
 * `webhookAdminRoutes()`, OUTSIDE the HTML app's JWT router — and JWT-exempt via AuthPlugin's
 * allowlist. The device-code endpoints are unauthenticated; everything else is gated by
 * [ApiBearerAuthPlugin]. This is the "JSON carve-out": these routes speak JSON, unlike the rest of
 * the app which returns HTML fragments.
 */
fun Route.apiV1Routes() {
    route("/api/v1") {
        // Device-code flow (unauthenticated — this is how the mod obtains its first token). These
        // live under `/auth/device-code`, distinct from the bearer-gated `/auth/token` node below.
        post("/auth/device-code") { call.handleCreateDeviceCode() }
        post("/auth/device-code/poll") { call.handlePollDeviceCode() }

        // Bearer-gated groups. The plugin is installed per sub-route (not on the shared `/api/v1`
        // node) so the unauthenticated device-code endpoints stay open.
        route("/auth/token") {
            install(ApiBearerAuthPlugin)
            delete { call.handleRevokeToken() }
        }
        route("/worlds") {
            install(ApiBearerAuthPlugin)
            // Container tagging (MCO-530) put writes under this node, so it needs the same demo
            // block the /projects group has. No-op for the GETs above it.
            install(ApiDemoWriteBlockPlugin)
            get { call.handleGetWorlds() }
            get("/{worldId}/projects") { call.handleGetWorldProjects() }
            get("/{worldId}/containers") { call.handleGetContainerTags() }
            post("/{worldId}/containers") { call.handleTagContainer() }
            delete("/{worldId}/containers/{containerId}") { call.handleUntagContainer() }
            // The HUD's frequent poll (~10s per player). Deliberately cheap — one indexed read.
            get("/{worldId}/storage") { call.handleGetWorldStorage() }
        }
        // The reporter's own surface (MCO-532). Gated by a plugin that accepts a world-scoped
        // reporter token OR a player token (the singleplayer path), never the player plugin.
        route("/reporter") {
            install(ApiReporterAuthPlugin)
            get("/tags") { call.handleGetReporterTags() }
            post("/contents") { call.handlePushReporterContents() }
        }
        route("/projects") {
            install(ApiBearerAuthPlugin)
            // Block demo-user writes in Production (reads stay open) — mirrors DemoUserPlugin.
            install(ApiDemoWriteBlockPlugin)
            post("/{projectId}/resources/sync") { call.handleSyncResources() }
            put("/{projectId}/tasks/{taskId}") { call.handleUpdateTask() }
        }
    }
}

// ── Device-code flow ───────────────────────────────────────────────────────────

private const val DEVICE_CODE_TTL_SECONDS = 600L
private const val DEVICE_CODE_INTERVAL_SECONDS = 5

/** The browser page a player enters their user code on. Built from APP_HOST; local dev falls back. */
private fun verificationUri(): String {
    val host = AppConfig.appHost
    return if (host.isNullOrBlank()) "${AppConfig.localBaseUrl}/link" else "https://$host/link"
}

suspend fun ApplicationCall.handleCreateDeviceCode() {
    val deviceCode = ApiCrypto.newToken()
    val userCode = ApiCrypto.newUserCode()
    val expiresAt = Instant.now().plusSeconds(DEVICE_CODE_TTL_SECONDS)
    when (CreateDeviceCodeStep.process(
        CreateDeviceCodeInput(deviceCode, userCode, expiresAt, DEVICE_CODE_INTERVAL_SECONDS)
    )) {
        is Result.Success -> respondJson(
            HttpStatusCode.OK,
            DeviceCodeResponse(
                deviceCode = deviceCode,
                userCode = userCode,
                verificationUri = verificationUri(),
                expiresIn = DEVICE_CODE_TTL_SECONDS,
                interval = DEVICE_CODE_INTERVAL_SECONDS,
            ),
        )
        is Result.Failure -> respondApiError(HttpStatusCode.InternalServerError, "server_error", "Could not create device code")
    }
}

suspend fun ApplicationCall.handlePollDeviceCode() {
    val body = receiveJsonOrNull<PollRequest>()
    if (body == null || body.deviceCode.isBlank()) {
        respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Missing device_code")
        return
    }
    val row = when (val r = GetDeviceCodeForPollStep.process(body.deviceCode)) {
        is Result.Success -> r.value
        // Unknown device code — treat as expired/invalid per RFC 8628 error semantics.
        is Result.Failure -> {
            respondJson(HttpStatusCode.BadRequest, PollPendingResponse("expired_token"))
            return
        }
    }

    val now = Instant.now()

    // slow_down: reject a poll that arrives faster than the advertised interval (window unchanged).
    if (row.lastPolledAt != null && Duration.between(row.lastPolledAt, now).seconds < row.intervalSeconds) {
        respondJson(HttpStatusCode.BadRequest, PollPendingResponse("slow_down"))
        return
    }
    TouchDeviceCodePolledStep.process(body.deviceCode)

    if (now.isAfter(row.expiresAt)) {
        ExpireDeviceCodeStep.process(body.deviceCode)
        respondJson(HttpStatusCode.BadRequest, PollPendingResponse("expired_token"))
        return
    }

    when (row.status) {
        "pending" -> respondJson(HttpStatusCode.BadRequest, PollPendingResponse("authorization_pending"))
        "denied" -> respondJson(HttpStatusCode.BadRequest, PollPendingResponse("access_denied"))
        "expired" -> respondJson(HttpStatusCode.BadRequest, PollPendingResponse("expired_token"))
        "approved" -> mintTokenForApprovedCode(body.deviceCode, row)
        else -> respondApiError(HttpStatusCode.InternalServerError, "server_error", "Unknown device code status")
    }
}

private suspend fun ApplicationCall.mintTokenForApprovedCode(deviceCode: String, row: DeviceCodePollRow) {
    val userId = row.userId
    if (userId == null) {
        respondApiError(HttpStatusCode.InternalServerError, "server_error", "Approved code has no bound user")
        return
    }
    // Atomically claim the one-time issuance; only the winning poll mints a token.
    val claimed = (ClaimDeviceCodeTokenStep.process(deviceCode) as? Result.Success)?.value ?: 0
    if (claimed != 1) {
        // Already issued (or lost the race) — the code is spent.
        respondJson(HttpStatusCode.BadRequest, PollPendingResponse("expired_token"))
        return
    }
    val token = ApiCrypto.newToken()
    val hash = ApiCrypto.sha256Hex(token)
    when (CreateApiTokenStep.process(CreateApiTokenInput(userId, hash, "Seam Companion Mod", null))) {
        is Result.Success -> {
            val username = (GetUsernameByIdStep.process(userId) as? Result.Success)?.value ?: ""
            respondJson(HttpStatusCode.OK, PollSuccessResponse(accessToken = token, username = username))
        }
        is Result.Failure -> respondApiError(HttpStatusCode.InternalServerError, "server_error", "Could not mint token")
    }
}

suspend fun ApplicationCall.handleRevokeToken() {
    RevokeApiTokenStep.process(getApiTokenHash())
    respondJson(HttpStatusCode.OK, OkResponse())
}

// ── Read/write ───────────────────────────────────────────────────────────────

suspend fun ApplicationCall.handleGetWorlds() {
    val userId = getApiUserId()
    when (val r = GetPermittedWorldsStep.process(GetPermittedWorldsInput(userId))) {
        is Result.Success -> respondJson(
            HttpStatusCode.OK,
            r.value.map {
                WorldDto(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    version = it.version.toString(),
                    totalProjects = it.totalProjects,
                    completedProjects = it.completedProjects,
                )
            },
        )
        is Result.Failure -> respondApiError(HttpStatusCode.InternalServerError, "server_error", "Could not load worlds")
    }
}

suspend fun ApplicationCall.handleGetWorldProjects() {
    val worldId = resolveWorldForUser() ?: return

    val projects = when (val r = GetProjectListStep(worldId).process(Unit)) {
        is Result.Success -> r.value
        is Result.Failure -> {
            respondApiError(HttpStatusCode.InternalServerError, "server_error", "Could not load projects")
            return
        }
    }

    val dtos = projects.map { p ->
        val resources = (GetAllResourceGatheringItemsStep.process(p.id) as? Result.Success)?.value.orEmpty()
        val tasks = (GetActionTasksForProjectStep.process(p.id) as? Result.Success)?.value.orEmpty()
        ProjectDto(
            id = p.id,
            name = p.name,
            stage = p.stage.name,
            state = p.state.name,
            resources = resources.map {
                ResourceDto(
                    itemId = it.itemId,
                    name = it.name,
                    required = it.required,
                    collected = it.collected,
                    sourceType = it.sourceType?.value,
                    progressSource = it.progressSource.value,
                )
            },
            tasks = tasks.map { TaskDto(id = it.id, name = it.name, completed = it.completed) },
        )
    }
    respondJson(HttpStatusCode.OK, dtos)
}

suspend fun ApplicationCall.handleSyncResources() {
    val projectId = parameters["projectId"]?.toIntOrNull()
    if (projectId == null) {
        respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Invalid project id")
        return
    }
    val worldId = resolveProjectForUser(projectId) ?: return

    val body = receiveJsonOrNull<SyncRequest>()
    if (body == null) {
        respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Malformed request body")
        return
    }

    // Project rollups + per-item counts before the sync (for the ResourceCountUpdated events).
    val before = (GetAllResourceGatheringItemsStep.process(projectId) as? Result.Success)?.value.orEmpty()
    val beforeByItem = before.associateBy { it.itemId }
    val projectPreviousDone = before.sumOf { it.collected }
    val projectRequired = before.sumOf { it.required }

    for (item in body.resources) {
        SetProgressByItemStep.process(SetProgressByItemInput(projectId, item.itemId, item.collected))
    }

    val after = (GetAllResourceGatheringItemsStep.process(projectId) as? Result.Success)?.value.orEmpty()
    val projectNewDone = after.sumOf { it.collected }

    // Publish one ResourceCountUpdated per changed tracked item (webhook parity — see
    // SetCollectedValuePipeline). Untracked items in the request are no-ops and skipped.
    val userId = getApiUserId()
    val username = (GetUsernameByIdStep.process(userId) as? Result.Success)?.value
    val projectName = (GetProjectByIdStep.process(projectId) as? Result.Success)?.value?.name
    val bus = eventBus
    for (a in after) {
        val prev = beforeByItem[a.itemId]?.collected ?: 0
        if (prev != a.collected) {
            bus.publish(
                ResourceCountUpdated(
                    worldId = worldId,
                    actorId = userId,
                    timestamp = Instant.now(),
                    projectId = projectId,
                    itemId = a.itemId,
                    previousDone = prev,
                    newDone = a.collected,
                    projectPreviousDone = projectPreviousDone,
                    projectNewDone = projectNewDone,
                    projectRequired = projectRequired,
                    actorName = username,
                    projectName = projectName,
                )
            )
        }
    }

    respondJson(
        HttpStatusCode.OK,
        ResourcesResponse(
            after.map {
                ResourceDto(
                    itemId = it.itemId,
                    name = it.name,
                    required = it.required,
                    collected = it.collected,
                    sourceType = it.sourceType?.value,
                    progressSource = it.progressSource.value,
                )
            }
        ),
    )
}

suspend fun ApplicationCall.handleUpdateTask() {
    val projectId = parameters["projectId"]?.toIntOrNull()
    val taskId = parameters["taskId"]?.toIntOrNull()
    if (projectId == null || taskId == null) {
        respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Invalid project or task id")
        return
    }
    resolveProjectForUser(projectId) ?: return

    val task = when (val r = GetActionTaskStep.process(taskId)) {
        is Result.Success -> r.value
        is Result.Failure -> {
            respondApiError(HttpStatusCode.NotFound, "not_found", "Task not found")
            return
        }
    }
    // The task must belong to the project in the URL.
    if (task.projectId != projectId) {
        respondApiError(HttpStatusCode.NotFound, "not_found", "Task not found in this project")
        return
    }

    val body = receiveJsonOrNull<TaskUpdateRequest>()
    if (body == null) {
        respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Malformed request body")
        return
    }

    when (SetTaskCompletedStep.process(SetTaskCompletedInput(taskId, body.completed))) {
        is Result.Success -> respondJson(
            HttpStatusCode.OK,
            TaskDto(id = task.id, name = task.name, completed = body.completed),
        )
        is Result.Failure -> respondApiError(HttpStatusCode.InternalServerError, "server_error", "Could not update task")
    }
}

// ── Container tags (MCO-530) ───────────────────────────────────────────────────

/**
 * Which blocks may be tagged — a whitelist, not "anything that is an `Inventory`". A furnace is an
 * `Inventory`, and tagging one would count its fuel and input, which is not what anyone means.
 * Mirrors the CHECK constraint on `container_tags.kind`; change both together.
 */
private val TAGGABLE_KINDS = setOf(
    "chest", "trapped_chest", "barrel", "shulker_box", "hopper", "dropper", "dispenser",
)

private const val MAX_DIMENSION_LENGTH = 64
private const val MAX_GROUP_KEY_LENGTH = 128

/** A container's identity when it stands alone: its own block position. */
private fun positionKey(x: Int, y: Int, z: Int) = "$x,$y,$z"

/**
 * Whether [groupKey] names this position or one orthogonally adjacent to it.
 *
 * A joined chest's two halves differ by one block on X or Z, and the client posts both with the
 * lower half's position as the shared key — so "own or adjacent" is exactly the set of legitimate
 * keys, and everything else is either a client bug or an attempt to collapse unrelated containers
 * into one dedupe group.
 */
private fun isOwnOrAdjacentPosition(groupKey: String, x: Int, y: Int, z: Int): Boolean {
    val parts = groupKey.split(',')
    if (parts.size != 3) return false
    val (kx, ky, kz) = parts.map { it.toIntOrNull() ?: return false }
    if (ky != y) return false
    val dx = Math.abs(kx.toLong() - x.toLong())
    val dz = Math.abs(kz.toLong() - z.toLong())
    return dx + dz <= 1
}

private fun ContainerTagRow.toDto() = ContainerTagDto(
    id = id,
    projectId = projectId,
    dimension = dimension,
    x = x,
    y = y,
    z = z,
    groupKey = groupKey,
    kind = kind,
    taggedBy = taggedByName,
    taggedAt = taggedAt.toString(),
    lastSeenAt = lastSeenAt?.toString(),
    state = state,
)

suspend fun ApplicationCall.handleGetContainerTags() {
    val worldId = resolveWorldForUser() ?: return
    when (val r = ListContainerTagsStep.process(worldId)) {
        is Result.Success -> respondJson(HttpStatusCode.OK, ContainerTagsResponse(r.value.map { it.toDto() }))
        is Result.Failure -> respondApiError(HttpStatusCode.InternalServerError, "server_error", "Could not load container tags")
    }
}

suspend fun ApplicationCall.handleTagContainer() {
    val worldId = resolveWorldForUser() ?: return

    val body = receiveJsonOrNull<ContainerTagRequest>()
    if (body == null) {
        respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Malformed request body")
        return
    }
    if (body.dimension.isBlank() || body.dimension.length > MAX_DIMENSION_LENGTH) {
        respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Invalid dimension")
        return
    }
    if (body.kind !in TAGGABLE_KINDS) {
        respondApiError(
            HttpStatusCode.BadRequest,
            "invalid_request",
            "Not a taggable container kind: ${body.kind}",
        )
        return
    }
    // Default the group key to the position, which is what an unjoined container's identity is.
    // Both halves of a double chest are posted with the same explicit key by the client.
    val groupKey = body.groupKey?.takeIf { it.isNotBlank() } ?: positionKey(body.x, body.y, body.z)
    if (groupKey.length > MAX_GROUP_KEY_LENGTH) {
        respondApiError(HttpStatusCode.BadRequest, "invalid_request", "group_key is too long")
        return
    }
    // The key is the sweep's dedupe key, so it cannot be free text. Unchecked, one client could
    // send the same key for a hundred unrelated chests and the sweep would count one of them —
    // silently erasing the rest from the measurement. Constraining it to this position or the one
    // next door admits exactly the case it exists for (a joined chest, whose halves are adjacent)
    // and nothing else.
    if (!isOwnOrAdjacentPosition(groupKey, body.x, body.y, body.z)) {
        respondApiError(
            HttpStatusCode.BadRequest,
            "invalid_request",
            "group_key must be this container's position or an adjacent one",
        )
        return
    }

    // The project must live in the world from the URL — otherwise membership of any world would be
    // enough to tag a container into someone else's project.
    val projectWorldId = when (val r = GetProjectWorldIdStep.process(body.projectId)) {
        is Result.Success -> r.value
        is Result.Failure -> {
            respondApiError(HttpStatusCode.NotFound, "not_found", "Project not found")
            return
        }
    }
    if (projectWorldId != worldId) {
        respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Project does not belong to this world")
        return
    }

    val input = UpsertContainerTagInput(
        worldId = worldId,
        projectId = body.projectId,
        dimension = body.dimension,
        x = body.x,
        y = body.y,
        z = body.z,
        groupKey = groupKey,
        kind = body.kind,
        taggedBy = getApiUserId(),
    )
    // Which project held this position before, if any. Re-tagging carries the container's stored
    // contents across to the new project, so BOTH rollups are wrong the moment the row moves: the
    // old one still claims stock it no longer has, and the new one does not yet show it. The next
    // sweep would fix it, but "wrong until something else happens" is not a state to ship — and
    // with no reporter running, nothing else happens.
    val priorProject = (FindContainerTagProjectByPositionStep.process(
        ContainerPositionKey(worldId, body.dimension, body.x, body.y, body.z)
    ) as? Result.Success)?.value

    when (val r = UpsertContainerTagStep.process(input)) {
        is Result.Success -> {
            if (priorProject != null && priorProject != body.projectId) {
                RecomputeMeasurementStep.process(priorProject)
            }
            RecomputeMeasurementStep.process(body.projectId)
            respondJson(HttpStatusCode.OK, r.value.toDto())
        }
        is Result.Failure -> respondApiError(HttpStatusCode.InternalServerError, "server_error", "Could not tag container")
    }
}

suspend fun ApplicationCall.handleUntagContainer() {
    val worldId = resolveWorldForUser() ?: return
    val containerId = parameters["containerId"]?.toLongOrNull()
    if (containerId == null) {
        respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Invalid container id")
        return
    }
    val key = ContainerTagKey(worldId, containerId)
    // Read the project before the delete takes the row away — the measurement has to be re-rolled
    // for it afterwards, and afterwards there is nothing left to ask.
    val projectId = (GetContainerTagProjectStep.process(key) as? Result.Success)?.value

    // Scoped by world, so a tag in another world reads as absent rather than as someone else's.
    when (val r = DeleteContainerTagStep.process(key)) {
        is Result.Success ->
            if (r.value == 0) {
                respondApiError(HttpStatusCode.NotFound, "not_found", "Container tag not found")
            } else {
                // `container_contents` cascades with the tag, so the chest's contribution is
                // already gone from the raw data — but the materialised rollup still claims it
                // until this runs. Untagging must drop the count immediately, without the reporter
                // needing to be told anything happened.
                projectId?.let { RecomputeMeasurementStep.process(it) }
                respondJson(HttpStatusCode.OK, OkResponse())
            }
        is Result.Failure -> respondApiError(HttpStatusCode.InternalServerError, "server_error", "Could not untag container")
    }
}

/**
 * Parses `{worldId}` and verifies the bearer user is a member of that world. Responds 400 (bad id)
 * or 403 (not a member) and returns null on failure; otherwise returns the world id.
 *
 * The membership check shares the web's participant definition (rejects non-members AND
 * world-banned members; `world_role <= MEMBER`). Not a member, or world absent → 403, never a
 * 404 that would leak whether the world exists.
 */
private suspend fun ApplicationCall.resolveWorldForUser(): Int? {
    val worldId = parameters["worldId"]?.toIntOrNull()
    if (worldId == null) {
        respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Invalid world id")
        return null
    }
    if (ValidateWorldMemberRole<Unit>(apiProfile(getApiUserId()), Role.MEMBER, worldId).process(Unit) is Result.Failure) {
        respondApiError(HttpStatusCode.Forbidden, "forbidden", "Not a member of this world")
        return null
    }
    return worldId
}

// ── Reporter endpoints and the count poll (MCO-532) ────────────────────────────

/**
 * Settles which world a reporter-gated call speaks for, and proves the caller may speak for it.
 *
 * A reporter token fixes its own world; naming a different one is a 403 rather than a silent
 * substitution, because a mismatch means the server is misconfigured and saying so is the whole
 * point of the diagnosable-failure design. A player token must name a world and be a member of it —
 * that is the singleplayer path, where the integrated server sweeps as the player.
 */
private suspend fun ApplicationCall.resolveReporterWorld(requestedWorldId: Int?): Int? {
    val reporterWorldId = getReporterWorldId()
    if (reporterWorldId != null) {
        if (requestedWorldId != null && requestedWorldId != reporterWorldId) {
            respondApiError(
                HttpStatusCode.Forbidden,
                "forbidden",
                "This token reports for world $reporterWorldId, not $requestedWorldId",
            )
            return null
        }
        return reporterWorldId
    }

    // Player token — the singleplayer path.
    val userId = getApiUserIdOrNull()
    if (userId == null) {
        respondApiError(HttpStatusCode.Unauthorized, "invalid_token", "Not authenticated")
        return null
    }
    if (requestedWorldId == null) {
        respondApiError(
            HttpStatusCode.BadRequest,
            "invalid_request",
            "world_id is required when reporting with a player token",
        )
        return null
    }
    if (ValidateWorldMemberRole<Unit>(apiProfile(userId), Role.MEMBER, requestedWorldId).process(Unit) is Result.Failure) {
        respondApiError(HttpStatusCode.Forbidden, "forbidden", "Not a member of this world")
        return null
    }
    return requestedWorldId
}

suspend fun ApplicationCall.handleGetReporterTags() {
    val worldId = resolveReporterWorld(request.queryParameters["world_id"]?.toIntOrNull()) ?: return

    val tags = when (val r = ListContainerTagsStep.process(worldId)) {
        is Result.Success -> r.value
        is Result.Failure -> {
            respondApiError(HttpStatusCode.InternalServerError, "server_error", "Could not load container tags")
            return
        }
    }
    val interest = when (val r = GetItemsOfInterestStep.process(worldId)) {
        is Result.Success -> r.value
        is Result.Failure -> {
            respondApiError(HttpStatusCode.InternalServerError, "server_error", "Could not load items of interest")
            return
        }
    }

    respondJson(
        HttpStatusCode.OK,
        ReporterTagsResponse(
            worldId = worldId,
            containers = tags.map { it.toDto() },
            itemsOfInterest = interest.map { (projectId, items) ->
                ItemsOfInterestDto(projectId = projectId, itemIds = items.sorted())
            }.sortedBy { it.projectId },
        ),
    )
}

suspend fun ApplicationCall.handlePushReporterContents() {
    val body = receiveJsonOrNull<ReporterContentsRequest>()
    if (body == null) {
        respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Malformed request body")
        return
    }
    val worldId = resolveReporterWorld(body.worldId) ?: return

    // A sweep with nothing to report still pushes, and that empty payload is the heartbeat — so an
    // empty container list is a valid request, not a rejected one.
    val containers = mutableListOf<ReportedContainer>()
    for (c in body.containers) {
        if (c.state !in REPORTABLE_STATES) {
            respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Unknown container state: ${c.state}")
            return
        }
        val seenAt = runCatching { Instant.parse(c.seenAt) }.getOrNull()
        if (seenAt == null) {
            respondApiError(HttpStatusCode.BadRequest, "invalid_request", "seen_at must be an ISO-8601 instant")
            return
        }
        if (c.items.any { it.count < 0 }) {
            respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Item counts cannot be negative")
            return
        }
        containers += ReportedContainer(
            id = c.id,
            state = c.state,
            seenAt = seenAt,
            items = c.items.map { ReportedItem(it.itemId, it.count) },
        )
    }

    when (val r = PushContainerContentsStep.process(PushContentsInput(worldId, containers))) {
        is Result.Success -> {
            // Best-effort: the push succeeded, and a failed stamp must not turn that into an error.
            getReporterTokenId()?.let {
                TouchReporterTokenStep.process(TouchReporterTokenInput(it, body.reporterVersion))
            }
            respondJson(
                HttpStatusCode.OK,
                ReporterContentsResponse(
                    accepted = r.value.accepted,
                    rejected = r.value.rejected,
                    projectsRecomputed = r.value.projectsRecomputed,
                ),
            )
        }
        is Result.Failure ->
            respondApiError(HttpStatusCode.InternalServerError, "server_error", "Could not store container contents")
    }
}

/** The states a sweep may report. Mirrors `container_tags.state`'s CHECK constraint. */
private val REPORTABLE_STATES = setOf("ok", "unreadable", "missing")

suspend fun ApplicationCall.handleGetWorldStorage() {
    val worldId = resolveWorldForUser() ?: return
    when (val r = GetWorldStorageStep.process(worldId)) {
        is Result.Success -> respondJson(
            HttpStatusCode.OK,
            r.value.map {
                WorldStorageDto(
                    projectId = it.projectId,
                    itemId = it.itemId,
                    measured = it.measured,
                    containerCount = it.containerCount,
                    oldestSeenAt = it.oldestSeenAt?.toString(),
                )
            },
        )
        is Result.Failure ->
            respondApiError(HttpStatusCode.InternalServerError, "server_error", "Could not load storage counts")
    }
}

/**
 * Resolves the world a project belongs to and verifies the bearer user is a member of it. Responds
 * 404 (project absent) or 403 (not a member) and returns null on failure; otherwise returns the
 * world id.
 */
private suspend fun ApplicationCall.resolveProjectForUser(projectId: Int): Int? {
    val userId = getApiUserId()
    val worldId = when (val r = GetProjectWorldIdStep.process(projectId)) {
        is Result.Success -> r.value
        is Result.Failure -> {
            respondApiError(HttpStatusCode.NotFound, "not_found", "Project not found")
            return null
        }
    }
    if (ValidateWorldMemberRole<Unit>(apiProfile(userId), Role.MEMBER, worldId).process(Unit) is Result.Failure) {
        respondApiError(HttpStatusCode.Forbidden, "forbidden", "Not a member of this project's world")
        return null
    }
    return worldId
}

/**
 * Minimal [TokenProfile] carrying only the bearer user's id — enough for [ValidateWorldMemberRole],
 * which keys solely off `user.id`. The API resolves an id (not a full profile) from the token.
 */
private fun apiProfile(userId: Int): TokenProfile =
    TokenProfile(id = userId, uuid = "", minecraftUsername = "", displayName = "", roles = emptyList())
