package app.mcorg.pipeline.resources

import app.mcorg.domain.model.resources.ItemSourceGraph
import app.mcorg.domain.model.resources.ProductionPath
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.services.ItemSourceGraphQueries
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.project.pathSelectorTree
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.serialization.json.Json

private val decoder = Json {
    allowStructuredMapKeys = true
}

/**
 * Handles the initial path selection view
 * GET /app/worlds/{worldId}/projects/{projectId}/resources/gathering/{gatheringId}/select-path
 * Query params: depth (default 2), maxBranches (default 3)
 */
suspend fun ApplicationCall.handleSelectResourcePath() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    val depth = request.queryParameters["depth"]?.toIntOrNull()?.takeIf { it in 1..10 } ?: 2
    val maxBranches = request.queryParameters["maxBranches"]?.toIntOrNull()?.takeIf { it in 1..10 } ?: 3
    val selectedPath = request.queryParameters["path"]?.let { ProductionPath.decode(it) }

    executeParallelPipeline(
        onSuccess = {
            if (it == null) {
                respondBadRequest("Item not found in production graph")
            } else {
                respondHtml(createHTML().div {
                    pathSelectorTree(worldId, projectId, resourceGatheringId, it, selectedPath, depth, maxBranches)
                })
            }
        }
    ) {
        val graph = singleStep("graph", worldId, GetGraphStep)
        val itemId = singleStep("itemId", resourceGatheringId, GetItemIdStep)

        merge("tree", graph, itemId) { g, id ->
            Result.success(
                ItemSourceGraphQueries(g)
                    .findProductionChain(id, depth)
                    ?.deduplicated()
                    ?.pruneRecursively(maxBranchesPerLevel = maxBranches)
            )
        }
    }
}

/**
 * Handles expanding a specific node in the tree
 * GET /app/worlds/{worldId}/projects/{projectId}/resources/gathering/{gatheringId}/select-path/expand
 * Query params: nodeItemId (required), depth (default 2), maxBranches (default 3), path (optional)
 */
suspend fun ApplicationCall.handleExpandPathNode() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    val nodeItemId = request.queryParameters["nodeItemId"]
    if (nodeItemId == null) {
        respondBadRequest("Missing nodeItemId parameter")
        return
    }

    val depth = request.queryParameters["depth"]?.toIntOrNull()?.takeIf { it in 1..10 } ?: 2
    val maxBranches = request.queryParameters["maxBranches"]?.toIntOrNull()?.takeIf { it in 1..10 } ?: 3
    val selectedPath = request.queryParameters["path"]?.let { ProductionPath.decode(it) }

    executePipeline(
        onSuccess = {
            if (it == null) {
                respondBadRequest("Item not found in production graph")
            } else {
                respondHtml(createHTML().div {
                    // Return expanded node HTML fragment
                    pathSelectorTree(worldId, projectId, resourceGatheringId, it, selectedPath, depth, maxBranches)
                })
            }
        }
    ) {
        value(worldId)
            .step(GetGraphStep)
            .map {
                ItemSourceGraphQueries(it)
                    .findProductionChain(nodeItemId, depth)
                    ?.deduplicated()
                    ?.pruneRecursively(maxBranchesPerLevel = maxBranches)
            }
    }
}

/**
 * Handles previewing the current selection
 * GET /app/worlds/{worldId}/projects/{projectId}/resources/gathering/{gatheringId}/select-path/preview
 * Query params: path (required)
 */
suspend fun ApplicationCall.handlePreviewPath() {
    val encodedPath = request.queryParameters["path"]
    if (encodedPath == null) {
        respondBadRequest("Missing path parameter")
        return
    }

    val path = ProductionPath.decode(encodedPath)
    if (path == null) {
        respondBadRequest("Invalid path encoding")
        return
    }

    respondHtml(createHTML().div {
        // TODO: Render preview/summary view
        div("path-summary") {
            +"Preview: ${path.getAllItemIds().size} unique items required"
        }
    })
}

private val GetGraphStep = DatabaseSteps.query<Int, ItemSourceGraph>(
    sql = SafeSQL.select(
        """
        SELECT graph_data FROM item_graph
        INNER JOIN world on item_graph.minecraft_version = world.version
        WHERE world.id = ?
    """.trimIndent()
    ),
    parameterSetter = { ps, worldId ->
        ps.setInt(1, worldId)
    },
    resultMapper = {
        it.next()
        decoder.decodeFromString<ItemSourceGraph>(it.getString("graph_data"))
    }
)

private val GetItemIdStep = DatabaseSteps.query<Int, String>(
    sql = SafeSQL.select(
        """
        SELECT item_id from resource_gathering
        WHERE id = ?
    """.trimIndent()
    ),
    parameterSetter = { ps, resourceGatheringId ->
        ps.setInt(1, resourceGatheringId)
    },
    resultMapper = {
        it.next()
        it.getString("item_id")
    }
)

