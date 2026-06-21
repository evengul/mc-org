package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.TargetTree
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.minecraft.GetItemSourceGraphForVersionStep
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.templated.dsl.pages.drillChainFragment
import app.mcorg.presentation.templated.dsl.pages.drillNotFoundFragment
import app.mcorg.presentation.templated.dsl.pages.nodePickerFragment
import app.mcorg.presentation.templated.dsl.pages.pickerNotFoundFragment
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * GET /worlds/{worldId}/projects/{projectId}/plan/chain/{itemId}
 *
 * Returns a drill-chain view fragment for one plan target. Swaps into #project-content
 * via outerHTML, exactly like the lens fragments.
 *
 * Path param `{itemId}` is URL-decoded before lookup — tag ids carry a leading `#`
 * that must be percent-encoded in URLs (`%23minecraft:planks` → `#minecraft:planks`).
 *
 * Graceful fallback:
 * - Plan derivation fails (no resources, all collected, no Minecraft graph):
 *   responds with a "no plan" info callout inside #project-content.
 * - Item not a target in the plan: responds with an "item not found in plan" info callout.
 */
suspend fun ApplicationCall.handleGetDrillChain() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    // URL-decode itemId so that %23minecraft:planks becomes #minecraft:planks
    val rawItemId = parameters["itemId"] ?: ""
    val itemId = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8)

    // Load project for display and URLs
    val project = when (val result = GetProjectByIdStep.process(projectId)) {
        is Result.Success -> result.value
        is Result.Failure -> {
            defaultHandleError(result.error)
            return
        }
    }

    // Derive the gathering plan — failure is graceful (no crash)
    val plan = when (val result = GenerateGatheringPlanStep.process(GatheringPlanInput(projectId, worldId))) {
        is Result.Success -> result.value
        is Result.Failure -> {
            val reason = when (result.error) {
                is AppFailure.ValidationError ->
                    "No gathering plan yet — add resources or there may be nothing left to gather."
                is AppFailure.DatabaseError.NotFound ->
                    "No Minecraft data found for this world's version. Try again after data ingestion."
                else -> "Could not derive the gathering plan."
            }
            respondHtml(drillNotFoundFragment(project, reason))
            return
        }
    }

    // Look up the target tree for this item
    val targetTree = plan.perTarget(itemId)
    if (targetTree == null) {
        respondHtml(drillNotFoundFragment(project, "'$itemId' is not a target in this plan."))
        return
    }

    // Obtain the graph to compute candidate counts per node
    val graph = getGraphForWorld(worldId)

    // Build candidateCounts map: item-id → number of source candidates in the graph
    val candidateCounts = buildCandidateCounts(targetTree, graph)

    respondHtml(drillChainFragment(project, targetTree, candidateCounts))
}

/**
 * GET /worlds/{worldId}/projects/{projectId}/plan/chain/{itemId}/sources?node={encodedNodeId}
 *
 * Returns a picker fragment for one node in the drill chain. The picker shows all candidate
 * sources (for a multi-source item) or all tag members (for an OPEN_TAG node).
 *
 * Responds with an innerHTML fragment for `#picker-{nodeSlug}`.
 */
suspend fun ApplicationCall.handleGetNodePicker() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val rawItemId = parameters["itemId"] ?: ""
    val targetItemId = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8)
    val rawNodeId = request.queryParameters["node"] ?: ""
    val nodeId = URLDecoder.decode(rawNodeId, StandardCharsets.UTF_8)

    if (nodeId.isBlank()) {
        respondBadRequest("Missing required query parameter: node")
        return
    }

    // Re-derive plan
    val plan = deriveOrNull(projectId, worldId) ?: run {
        respondHtml(pickerNotFoundFragment("Plan not available — try refreshing."))
        return
    }

    val targetTree = plan.perTarget(targetItemId) ?: run {
        respondHtml(pickerNotFoundFragment("Target '$targetItemId' not found in plan."))
        return
    }

    val node = findNodeById(targetTree, nodeId) ?: run {
        respondHtml(pickerNotFoundFragment("Node '$nodeId' not found in chain."))
        return
    }

    val graph = getGraphForWorld(worldId)

    // Load current overrides so we can highlight the active selection
    val overrides = when (val r = GetPlanOverridesStep.process(projectId)) {
        is Result.Success -> r.value
        is Result.Failure -> null
    }

    respondHtml(
        nodePickerFragment(
            worldId = worldId,
            projectId = projectId,
            targetItemId = targetItemId,
            node = node,
            graph = graph,
            activeSourceKey = overrides?.sourceByItem?.get(nodeId),
            activeMemberId = overrides?.tagMember?.get(nodeId),
        )
    )
}

/**
 * POST /worlds/{worldId}/projects/{projectId}/plan/chain/{itemId}/pin
 *
 * Pins a specific source for a node. Form params: `node` (item id), `sourceKey`.
 * Re-derives the plan and re-renders the full drill chain into #project-content.
 */
suspend fun ApplicationCall.handlePinSource() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val rawItemId = parameters["itemId"] ?: ""
    val targetItemId = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8)

    val params = receiveParameters()
    val nodeId = params["node"]
    val sourceKey = params["sourceKey"]

    if (nodeId.isNullOrBlank()) {
        respondBadRequest(
            buildValidationError("node", "The parameter 'node' is required."),
            target = "#error-message",
            swap = "innerHTML"
        )
        return
    }
    if (sourceKey.isNullOrBlank()) {
        respondBadRequest(
            buildValidationError("sourceKey", "The parameter 'sourceKey' is required."),
            target = "#error-message",
            swap = "innerHTML"
        )
        return
    }

    // Persist the override
    when (val r = UpsertPlanOverrideStep(projectId).process(PlanOverride.Source(nodeId, sourceKey))) {
        is Result.Failure -> {
            defaultHandleError(r.error)
            return
        }
        is Result.Success -> { /* continue */ }
    }

    respondDrillRerender(worldId, projectId, targetItemId)
}

/**
 * POST /worlds/{worldId}/projects/{projectId}/plan/chain/{itemId}/tag
 *
 * Picks a concrete member item for a tag node. Form params: `node` (tag id), `memberItemId`.
 * Re-derives the plan and re-renders the full drill chain into #project-content.
 */
suspend fun ApplicationCall.handleResolveTagMember() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val rawItemId = parameters["itemId"] ?: ""
    val targetItemId = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8)

    val params = receiveParameters()
    val nodeId = params["node"]
    val memberItemId = params["memberItemId"]

    if (nodeId.isNullOrBlank()) {
        respondBadRequest(
            buildValidationError("node", "The parameter 'node' is required."),
            target = "#error-message",
            swap = "innerHTML"
        )
        return
    }
    if (memberItemId.isNullOrBlank()) {
        respondBadRequest(
            buildValidationError("memberItemId", "The parameter 'memberItemId' is required."),
            target = "#error-message",
            swap = "innerHTML"
        )
        return
    }

    // Persist the override
    when (val r = UpsertPlanOverrideStep(projectId).process(PlanOverride.TagMember(nodeId, memberItemId))) {
        is Result.Failure -> {
            defaultHandleError(r.error)
            return
        }
        is Result.Success -> { /* continue */ }
    }

    respondDrillRerender(worldId, projectId, targetItemId)
}

/**
 * DELETE /worlds/{worldId}/projects/{projectId}/plan/chain/{itemId}/override?node={encodedNodeId}
 *
 * Clears the override for a node. Re-derives the plan and re-renders the full drill chain.
 */
suspend fun ApplicationCall.handleClearOverride() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val rawItemId = parameters["itemId"] ?: ""
    val targetItemId = URLDecoder.decode(rawItemId, StandardCharsets.UTF_8)

    val rawNodeId = request.queryParameters["node"] ?: ""
    val nodeId = URLDecoder.decode(rawNodeId, StandardCharsets.UTF_8)

    if (nodeId.isBlank()) {
        respondBadRequest("Missing required query parameter: node")
        return
    }

    when (val r = ClearPlanOverrideStep(projectId).process(nodeId)) {
        is Result.Failure -> {
            defaultHandleError(r.error)
            return
        }
        is Result.Success -> { /* continue */ }
    }

    respondDrillRerender(worldId, projectId, targetItemId)
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

/**
 * Shared re-render: re-derives the plan and responds with [drillChainFragment].
 * Falls back to [drillNotFoundFragment] when derivation or target lookup fails.
 */
private suspend fun ApplicationCall.respondDrillRerender(
    worldId: Int,
    projectId: Int,
    targetItemId: String,
) {
    val project = when (val r = GetProjectByIdStep.process(projectId)) {
        is Result.Success -> r.value
        is Result.Failure -> {
            defaultHandleError(r.error)
            return
        }
    }

    val plan = deriveOrNull(projectId, worldId)
    val targetTree = plan?.perTarget(targetItemId)

    if (plan == null || targetTree == null) {
        val reason = if (plan == null)
            "Could not derive the gathering plan."
        else
            "'$targetItemId' is not a target in this plan."
        respondHtml(drillNotFoundFragment(project, reason))
        return
    }

    val graph = getGraphForWorld(worldId)
    val candidateCounts = buildCandidateCounts(targetTree, graph)
    respondHtml(drillChainFragment(project, targetTree, candidateCounts))
}

/**
 * Derives the gathering plan and returns it, or null on any failure (graceful).
 */
private suspend fun deriveOrNull(projectId: Int, worldId: Int): GatheringPlan? =
    when (val r = GenerateGatheringPlanStep.process(GatheringPlanInput(projectId, worldId))) {
        is Result.Success -> r.value
        is Result.Failure -> null
    }

/**
 * Recursively walks a [TargetTree] looking for a node whose item id matches [nodeId].
 * Returns the first match found in depth-first order, or null.
 */
internal fun findNodeById(root: TargetTree, nodeId: String): TargetTree? {
    if (root.item.id == nodeId) return root
    for (child in root.children) {
        findNodeById(child, nodeId)?.let { return it }
    }
    return null
}

/**
 * Obtains the [ItemSourceGraph] for the world's Minecraft version.
 * Returns null when the version or graph is not found (graceful — callers treat it as empty).
 */
internal suspend fun getGraphForWorld(worldId: Int): ItemSourceGraph? {
    val versionString = GetWorldVersionStep.process(worldId).getOrNull() ?: return null
    return GetItemSourceGraphForVersionStep.process(versionString).getOrNull()
}

/**
 * Walks the [TargetTree] recursively and for each unique item queries the graph
 * for its source candidate count, building a flat map of item-id → count.
 * Items not present in the graph get 0 (treated as forced/no-chip).
 */
internal fun buildCandidateCounts(
    root: TargetTree,
    graph: ItemSourceGraph?,
): Map<String, Int> {
    if (graph == null) return emptyMap()

    val result = mutableMapOf<String, Int>()
    fun walk(node: TargetTree) {
        if (node.item.id !in result) {
            val sources = graph.getSourcesForItem(node.item)
            result[node.item.id] = sources.size
        }
        for (child in node.children) walk(child)
    }
    walk(root)
    return result
}

/** Builds a minimal validation error HTML string for respondBadRequest. */
private fun buildValidationError(param: String, message: String): String =
    "<p class=\"validation-error-message\" id=\"validation-error-$param\">$message</p>"
