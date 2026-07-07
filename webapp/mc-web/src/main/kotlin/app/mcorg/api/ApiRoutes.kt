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
            get { call.handleGetWorlds() }
            get("/{worldId}/projects") { call.handleGetWorldProjects() }
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
    return if (host.isNullOrBlank()) "http://localhost:8080/link" else "https://$host/link"
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
    val userId = getApiUserId()
    val worldId = parameters["worldId"]?.toIntOrNull()
    if (worldId == null) {
        respondApiError(HttpStatusCode.BadRequest, "invalid_request", "Invalid world id")
        return
    }
    // Membership check: shares the web's participant definition (rejects non-members AND
    // world-banned members; world_role <= MEMBER). Not a member (or world absent) → 403.
    if (ValidateWorldMemberRole<Unit>(apiProfile(userId), Role.MEMBER, worldId).process(Unit) is Result.Failure) {
        respondApiError(HttpStatusCode.Forbidden, "forbidden", "Not a member of this world")
        return
    }

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
                    sourceType = it.sourceType,
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
                    sourceType = it.sourceType,
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
