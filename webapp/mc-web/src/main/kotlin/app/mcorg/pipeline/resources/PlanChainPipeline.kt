package app.mcorg.pipeline.resources

import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.plan.TargetTree
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.GetItemSourceGraphForVersionStep
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.templated.dsl.pages.drillChainFragment
import app.mcorg.presentation.templated.dsl.pages.drillNotFoundFragment
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
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
 * Obtains the [ItemSourceGraph] for the world's Minecraft version.
 * Returns null when the version or graph is not found (graceful — callers treat it as empty).
 */
private suspend fun getGraphForWorld(worldId: Int): ItemSourceGraph? {
    val versionString = GetWorldVersionStep.process(worldId).getOrNull() ?: return null
    return GetItemSourceGraphForVersionStep.process(versionString).getOrNull()
}

/**
 * Walks the [TargetTree] recursively and for each unique item queries the graph
 * for its source candidate count, building a flat map of item-id → count.
 * Items not present in the graph get 0 (treated as forced/no-chip).
 */
private fun buildCandidateCounts(
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
