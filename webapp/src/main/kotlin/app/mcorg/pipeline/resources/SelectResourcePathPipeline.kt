package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ItemSourceGraph
import app.mcorg.domain.model.resources.ProductionPath
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.services.ItemSourceGraphBuilder
import app.mcorg.domain.services.ItemSourceGraphQueries
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.project.pathSelectorTree
import app.mcorg.presentation.templated.project.pathSummary
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML

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

    executeParallelPipeline(
        onSuccess = { (tree, plan) ->
            if (tree == null) {
                respondBadRequest("Item not found in production graph")
            } else {
                respondHtml(createHTML().div {
                    pathSelectorTree(worldId, projectId, resourceGatheringId, tree, plan.getOrNull()?.selectedPath, depth, maxBranches, plan.getOrNull()?.confirmed ?: false)
                })
            }
        }
    ) {
        val graph = singleStep("graph", worldId, GetGraphStep)
        val itemId = singleStep("itemId", resourceGatheringId, GetItemIdStep)
        val savedPlan = singleStep("plan", resourceGatheringId, LoadSavedPathStep)

        val tree = merge("tree", graph, itemId) { g, id ->
            Result.success(
                ItemSourceGraphQueries(g)
                    .findProductionChain(id, depth)
                    ?.deduplicated()
                    ?.pruneRecursively(maxBranchesPerLevel = maxBranches)
            )
        }

        merge("result", tree, savedPlan) { t, p ->
            Result.success(t to p)
        }
    }
}

/**
 * Handles expanding a specific node in the tree
 * GET /app/worlds/{worldId}/projects/{projectId}/resources/gathering/{gatheringId}/select-path/expand
 * Query params: nodeItemId (required), depth (default 2), maxBranches (default 3)
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

    executeParallelPipeline(
        onSuccess = { (tree, plan) ->
            if (tree == null) {
                respondBadRequest("Item not found in production graph")
            } else {
                respondHtml(createHTML().div {
                    pathSelectorTree(worldId, projectId, resourceGatheringId, tree, plan.getOrNull()?.selectedPath, depth, maxBranches, plan.getOrNull()?.confirmed ?: false)
                })
            }
        }
    ) {
        val graph = singleStep("graph", worldId, GetGraphStep)
        val savedPlan = singleStep("plan", resourceGatheringId, LoadSavedPathStep)

        merge("result", graph, savedPlan) { g, plan ->
            val tree = ItemSourceGraphQueries(g)
                .findProductionChain(nodeItemId, depth)
                ?.deduplicated()
                ?.pruneRecursively(maxBranchesPerLevel = maxBranches)
            Result.success(tree to plan)
        }
    }
}

/**
 * Saves a path selection to the database
 * PUT /app/worlds/{worldId}/projects/{projectId}/resources/gathering/{gatheringId}/select-path
 * Form params: itemId (item being configured), sourceType (source selected for it)
 */
suspend fun ApplicationCall.handleSaveResourcePath() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    val parameters = receiveParameters()
    val selectedItemId = parameters["itemId"]
    val sourceType = parameters["sourceType"]
    if (selectedItemId.isNullOrBlank() || sourceType.isNullOrBlank()) {
        respondBadRequest("Missing itemId or sourceType parameter")
        return
    }

    val depth = request.queryParameters["depth"]?.toIntOrNull()?.takeIf { it in 1..10 } ?: 2
    val maxBranches = request.queryParameters["maxBranches"]?.toIntOrNull()?.takeIf { it in 1..10 } ?: 3

    // Load graph and build tree first (needed for populating requirements)
    val graphResult = GetGraphStep.process(worldId)
    if (graphResult is Result.Failure) {
        respondBadRequest("Failed to load production graph")
        return
    }
    val graph = (graphResult as Result.Success).value

    val itemIdResult = GetItemIdStep.process(resourceGatheringId)
    if (itemIdResult is Result.Failure) {
        respondBadRequest("Failed to load item")
        return
    }
    val rootItemId = (itemIdResult as Result.Success).value

    val tree = ItemSourceGraphQueries(graph)
        .findProductionChain(rootItemId, depth)
        ?.deduplicated()
        ?.pruneRecursively(maxBranchesPerLevel = maxBranches)

    if (tree == null) {
        respondBadRequest("Item not found in production graph")
        return
    }

    // Load existing path, apply selection with tree context, upsert
    val existingPlan = LoadSavedPathStep.process(resourceGatheringId)
    val existingPath = (existingPlan as? Result.Success)?.value?.getOrNull()?.selectedPath

    val updatedPath = (existingPath ?: ProductionPath(item = tree.targetItem.item))
        .selectSourceForItem(selectedItemId, sourceType, tree)

    val upsertResult = UpsertPathStep.process(resourceGatheringId to updatedPath)
    if (upsertResult is Result.Failure) {
        respondBadRequest("Failed to save path")
        return
    }

    respondHtml(createHTML().div {
        pathSelectorTree(worldId, projectId, resourceGatheringId, tree, updatedPath, depth, maxBranches, confirmed = false)
    })
}

/**
 * Confirms a path selection
 * PUT /app/worlds/{worldId}/projects/{projectId}/resources/gathering/{gatheringId}/select-path/confirm
 */
suspend fun ApplicationCall.handleConfirmResourcePath() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    executePipeline(
        onSuccess = { plan ->
            if (plan.getOrNull() == null) {
                respondBadRequest("No path saved yet")
            } else {
                respondHtml(createHTML().div {
                    pathSummary(worldId, projectId, resourceGatheringId, plan.getOrNull()?.selectedPath, confirmed = true)
                })
            }
        }
    ) {
        value(resourceGatheringId)
            .step(ConfirmPathStep)
            .map { resourceGatheringId }
            .step(LoadSavedPathStep)
    }
}

/**
 * Resets (deletes) a saved path selection so the user can start over
 * DELETE /app/worlds/{worldId}/projects/{projectId}/resources/gathering/{gatheringId}/select-path
 */
suspend fun ApplicationCall.handleResetResourcePath() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    val depth = request.queryParameters["depth"]?.toIntOrNull()?.takeIf { it in 1..10 } ?: 2
    val maxBranches = request.queryParameters["maxBranches"]?.toIntOrNull()?.takeIf { it in 1..10 } ?: 3

    // Delete the saved plan
    val deleteResult = DeletePathStep.process(resourceGatheringId)
    if (deleteResult is Result.Failure) {
        respondBadRequest("Failed to reset path")
        return
    }

    // Rebuild the tree for fresh selection
    val graphResult = GetGraphStep.process(worldId)
    if (graphResult is Result.Failure) {
        respondBadRequest("Failed to load production graph")
        return
    }
    val graph = (graphResult as Result.Success).value

    val itemIdResult = GetItemIdStep.process(resourceGatheringId)
    if (itemIdResult is Result.Failure) {
        respondBadRequest("Failed to load item")
        return
    }
    val rootItemId = (itemIdResult as Result.Success).value

    val tree = ItemSourceGraphQueries(graph)
        .findProductionChain(rootItemId, depth)
        ?.deduplicated()
        ?.pruneRecursively(maxBranchesPerLevel = maxBranches)

    if (tree == null) {
        respondBadRequest("Item not found in production graph")
        return
    }

    respondHtml(createHTML().div {
        pathSelectorTree(worldId, projectId, resourceGatheringId, tree, null, depth, maxBranches, confirmed = false)
    })
}

private val DeletePathStep = DatabaseSteps.update<Int>(
    sql = SafeSQL.delete(
        """
        DELETE FROM resource_gathering_plan
        WHERE resource_gathering_id = ?
        """.trimIndent()
    ),
    parameterSetter = { ps, resourceGatheringId ->
        ps.setInt(1, resourceGatheringId)
    }
)

private val GetWorldVersionStep = DatabaseSteps.query<Int, String>(
    sql = SafeSQL.select("SELECT version FROM world WHERE id = ?"),
    parameterSetter = { ps, worldId -> ps.setInt(1, worldId) },
    resultMapper = { rs ->
        rs.next()
        rs.getString("version")
    }
)

private val GetResourceSourcesStep = DatabaseSteps.query<String, List<ResourceSource>>(
    sql = SafeSQL.select(
        """
        SELECT rs.id as source_id, rs.source_type, rs.created_from_filename,
               pi.item as produced_item, pi.count as produced_count,
               ci.item as consumed_item, ci.count as consumed_count
        FROM resource_source rs
        LEFT JOIN resource_source_produced_item pi ON pi.resource_source_id = rs.id AND pi.version = rs.version
        LEFT JOIN (
            SELECT resource_source_id, version, item, count
            FROM resource_source_consumed_item
            UNION ALL
            SELECT ct.resource_source_id, ct.version, cti.item, ct.count
            FROM resource_source_consumed_tag ct
            JOIN minecraft_tag_item cti ON cti.tag = ct.tag AND cti.version = ct.version
        ) ci ON ci.resource_source_id = rs.id AND ci.version = rs.version
        WHERE rs.version = ?
        ORDER BY rs.id
    """.trimIndent()
    ),
    parameterSetter = { ps, version -> ps.setString(1, version) },
    resultMapper = { rs ->
        val sourcesMap = linkedMapOf<Int, Triple<ResourceSource.SourceType, String, MutableList<Pair<Item, Int>>>>()
        val producedMap = mutableMapOf<Int, MutableSet<Pair<String, Int>>>()
        val consumedMap = mutableMapOf<Int, MutableSet<Pair<String, Int>>>()

        while (rs.next()) {
            val sourceId = rs.getInt("source_id")
            val sourceType = ResourceSource.SourceType.of(rs.getString("source_type")) ?: ResourceSource.SourceType.UNKNOWN
            val filename = rs.getString("created_from_filename")

            sourcesMap.getOrPut(sourceId) { Triple(sourceType, filename, mutableListOf()) }

            val producedItem = rs.getString("produced_item")
            if (producedItem != null) {
                producedMap.getOrPut(sourceId) { mutableSetOf() }.add(producedItem to rs.getInt("produced_count"))
            }

            val consumedItem = rs.getString("consumed_item")
            if (consumedItem != null) {
                consumedMap.getOrPut(sourceId) { mutableSetOf() }.add(consumedItem to rs.getInt("consumed_count"))
            }
        }

        sourcesMap.map { (sourceId, triple) ->
            val (sourceType, filename, _) = triple
            ResourceSource(
                type = sourceType,
                filename = filename,
                producedItems = producedMap[sourceId]?.map { (item, count) ->
                    Item(item, item) as app.mcorg.domain.model.minecraft.MinecraftId to
                        if (count > 0) ResourceQuantity.ItemQuantity(count) else ResourceQuantity.Unknown
                } ?: emptyList(),
                requiredItems = consumedMap[sourceId]?.map { (item, count) ->
                    Item(item, item) as app.mcorg.domain.model.minecraft.MinecraftId to
                        if (count > 0) ResourceQuantity.ItemQuantity(count) else ResourceQuantity.Unknown
                } ?: emptyList()
            )
        }
    }
)

private object GetGraphStep : Step<Int, AppFailure.DatabaseError, ItemSourceGraph> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, ItemSourceGraph> {
        val versionResult = GetWorldVersionStep.process(input)
        if (versionResult is Result.Failure) return versionResult

        val sourcesResult = GetResourceSourcesStep.process((versionResult as Result.Success).value)
        if (sourcesResult is Result.Failure) return sourcesResult

        return Result.success(ItemSourceGraphBuilder.buildFromResourceSources((sourcesResult as Result.Success).value))
    }
}

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

