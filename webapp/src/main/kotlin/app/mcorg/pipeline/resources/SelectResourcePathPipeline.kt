package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ItemSourceGraph
import app.mcorg.domain.model.resources.ProductionPath
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.services.ItemSourceGraphBuilder
import app.mcorg.domain.services.ItemSourceGraphQueries
import app.mcorg.domain.services.PathSuggestionService
import app.mcorg.domain.services.SuggestionContext
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.project.pathPlanView
import app.mcorg.presentation.templated.project.pathSelectorTree
import app.mcorg.presentation.templated.project.pathSummary
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.slf4j.LoggerFactory

/**
 * Returns a lightweight read-only view for items that already have a saved plan.
 * GET /app/worlds/{worldId}/projects/{projectId}/resources/gathering/{gatheringId}/plan-view
 * Used for auto-loading on page render — no graph building required.
 */
suspend fun ApplicationCall.handleGetPlanView() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    when (val planResult = LoadSavedPathStep.process(resourceGatheringId)) {
        is Result.Failure -> respondNotFound()
        is Result.Success -> when (val plan = planResult.value) {
            is Result.Failure -> respondNotFound()
            is Result.Success -> respondHtml(createHTML().div {
                pathPlanView(worldId, projectId, resourceGatheringId, plan.value)
            })
        }
    }
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
               pi.item as produced_item, pi.count as produced_item_count, pin.item_name as produced_item_name,
               ci.item as consumed_item, ci.count as consumed_item_count, cin.item_name as consumed_item_name,
               pt.tag AS produced_tag, pt.count AS produced_tag_count, ptn.name as produced_tag_name,
               (SELECT STRING_AGG(mti.item, ',') FROM minecraft_tag_item mti WHERE mti.tag = pt.tag AND mti.version = rs.version) AS produced_tag_content,
               ct.tag AS consumed_tag, ct.count AS consumed_tag_count, ctn.name as consumed_tag_name,
               (SELECT STRING_AGG(mti.item, ',') FROM minecraft_tag_item mti WHERE mti.tag = ct.tag AND mti.version = rs.version) AS consumed_tag_content
        FROM resource_source rs
        LEFT JOIN resource_source_produced_item pi ON pi.resource_source_id = rs.id AND pi.version = rs.version
        LEFT JOIN minecraft_items pin ON pi.item = pin.item_id AND pi.version = rs.version
        LEFT JOIN resource_source_consumed_item ci ON ci.resource_source_id = rs.id AND ci.version = rs.version
        LEFT JOIN minecraft_items cin ON cin.item_id = ci.item AND cin.version = rs.version
        LEFT JOIN resource_source_produced_tag pt ON pt.resource_source_id = rs.id AND pt.version = rs.version
        LEFT JOIN minecraft_tag ptn ON ptn.tag = pt.tag AND ptn.version = rs.version
        LEFT JOIN resource_source_consumed_tag ct ON ct.resource_source_id = rs.id AND ct.version = rs.version
        LEFT JOIN minecraft_tag ctn ON ctn.tag = ct.tag AND ctn.version = rs.version
        WHERE rs.version = ?
        ORDER BY rs.id
    """.trimIndent()
    ),
    parameterSetter = { ps, version -> ps.setString(1, version) },
    resultMapper = { rs ->
        val sourcesMap = linkedMapOf<Int, Triple<ResourceSource.SourceType, String, MutableList<Pair<Item, Int>>>>()
        val producedMap = mutableMapOf<Int, MutableSet<Pair<String, Triple<Int, String, String>>>>()
        val consumedMap = mutableMapOf<Int, MutableSet<Pair<String, Triple<Int, String, String>>>>()

        while (rs.next()) {
            val sourceId = rs.getInt("source_id")
            val sourceType = ResourceSource.SourceType.of(rs.getString("source_type")) ?: ResourceSource.SourceType.UNKNOWN
            val filename = rs.getString("created_from_filename")

            sourcesMap.getOrPut(sourceId) { Triple(sourceType, filename, mutableListOf()) }

            val producedItem = rs.getString("produced_item")
            if (producedItem != null) {
                producedMap.getOrPut(sourceId) { mutableSetOf() }.add(producedItem to Triple(rs.getInt("produced_item_count"), rs.getString("produced_item_name"), ""))
            }
            val producedTag = rs.getString("produced_tag")
            if (producedTag != null) {
                val tagContent = rs.getString("produced_tag_content") ?: ""
                producedMap.getOrPut(sourceId) { mutableSetOf() }.add(producedTag to Triple(rs.getInt("produced_tag_count"), rs.getString("produced_tag_name"), tagContent))
            }

            val consumedItem = rs.getString("consumed_item")
            if (consumedItem != null) {
                consumedMap.getOrPut(sourceId) { mutableSetOf() }.add(consumedItem to Triple(rs.getInt("consumed_item_count"), rs.getString("consumed_item_name"), ""))
            }
            val consumedTag = rs.getString("consumed_tag")
            if (consumedTag != null) {
                val tagContent = rs.getString("consumed_tag_content") ?: ""
                consumedMap.getOrPut(sourceId) { mutableSetOf() }.add(consumedTag to Triple(rs.getInt("consumed_tag_count"), rs.getString("consumed_tag_name"), tagContent))
            }
        }

        sourcesMap.map { (sourceId, triple) ->
            val (sourceType, filename, _) = triple
            ResourceSource(
                type = sourceType,
                filename = filename,
                producedItems = producedMap[sourceId]?.map { (item, countNameContent) ->
                    val (count, name, tagContent) = countNameContent
                    when {
                        item.startsWith("#") -> MinecraftTag(item, name, tagContent.split(",").filter { it.isNotEmpty() }.map { Item(it, it) }) to (if (count > 0) ResourceQuantity.ItemQuantity(count) else ResourceQuantity.Unknown)
                        else -> Item(item, name) to (if (count > 0) ResourceQuantity.ItemQuantity(count) else ResourceQuantity.Unknown)
                    }
                } ?: emptyList(),
                requiredItems = consumedMap[sourceId]?.map { (item, countNameContent) ->
                    val (count, name, tagContent) = countNameContent
                    when {
                        item.startsWith("#") -> MinecraftTag(item, name, tagContent.split(",").filter { it.isNotEmpty() }.map { Item(it, it) }) to (if (count > 0) ResourceQuantity.ItemQuantity(count) else ResourceQuantity.Unknown)
                        else -> Item(item, name) to (if (count > 0) ResourceQuantity.ItemQuantity(count) else ResourceQuantity.Unknown)
                    }
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

private val GetRequiredAmountStep = DatabaseSteps.query<Int, Int>(
    sql = SafeSQL.select("SELECT required FROM resource_gathering WHERE id = ?"),
    parameterSetter = { ps, resourceGatheringId -> ps.setInt(1, resourceGatheringId) },
    resultMapper = { rs ->
        if (!rs.next()) throw NoSuchElementException("Resource gathering item not found")
        rs.getInt("required")
    }
)

private val GetWorldProductionItemIdsStep = DatabaseSteps.query<Int, Set<String>>(
    sql = SafeSQL.select(
        """
        SELECT DISTINCT pp.item_id
        FROM project_productions pp
        JOIN projects p ON p.id = pp.project_id
        WHERE p.world_id = ?
    """.trimIndent()
    ),
    parameterSetter = { ps, worldId -> ps.setInt(1, worldId) },
    resultMapper = { rs ->
        val items = mutableSetOf<String>()
        while (rs.next()) {
            items.add(rs.getString("item_id"))
        }
        items
    }
)

/**
 * Auto-suggests a production path for a single resource gathering item.
 * POST /app/worlds/{worldId}/projects/{projectId}/resources/gathering/{gatheringId}/suggest-path
 * Query params: recipeThreshold (default 100), depth (default 5), maxBranches (default 5)
 */
suspend fun ApplicationCall.handleSuggestResourcePath() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    val recipeThreshold = request.queryParameters["recipeThreshold"]?.toIntOrNull()?.takeIf { it > 0 } ?: 100
    val depth = request.queryParameters["depth"]?.toIntOrNull()?.takeIf { it in 1..10 } ?: 5
    val maxBranches = request.queryParameters["maxBranches"]?.toIntOrNull()?.takeIf { it in 1..10 } ?: 5

    // Load all needed data
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

    val requiredAmountResult = GetRequiredAmountStep.process(resourceGatheringId)
    if (requiredAmountResult is Result.Failure) {
        respondBadRequest("Failed to load required amount")
        return
    }
    val requiredAmount = (requiredAmountResult as Result.Success).value

    val worldProductionsResult = GetWorldProductionItemIdsStep.process(worldId)
    val worldProductions = (worldProductionsResult as? Result.Success)?.value ?: emptySet()

    // Build the production tree with higher depth/branches for suggestion
    val tree = ItemSourceGraphQueries(graph)
        .findProductionChain(rootItemId, depth)
        ?.deduplicated()
        ?.pruneRecursively(maxBranchesPerLevel = maxBranches)

    if (tree == null) {
        respondBadRequest("Item not found in production graph")
        return
    }

    // Run the suggestion algorithm
    val context = SuggestionContext(
        requiredAmount = requiredAmount,
        worldProductions = worldProductions,
        recipeThreshold = recipeThreshold
    )
    val suggestedPath = PathSuggestionService.suggestPath(tree, context)

    // Save as unconfirmed plan
    val upsertResult = UpsertPathStep.process(resourceGatheringId to suggestedPath)
    if (upsertResult is Result.Failure) {
        respondBadRequest("Failed to save suggested path")
        return
    }

    // Return the path selector UI with the suggestion pre-populated
    respondHtml(createHTML().div {
        pathSelectorTree(worldId, projectId, resourceGatheringId, tree, suggestedPath, depth, maxBranches, confirmed = false)
    })
}

/**
 * Auto-suggests production paths for all resource gathering items in a project.
 * POST /app/worlds/{worldId}/projects/{projectId}/resources/suggest-all-paths
 * Query params: recipeThreshold (default 100)
 *
 * Returns an HX-Redirect to reload the project page with updated plans.
 */
suspend fun ApplicationCall.handleSuggestAllResourcePaths() {
    val logger = LoggerFactory.getLogger("SuggestAllResourcePaths")
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()

    val recipeThreshold = request.queryParameters["recipeThreshold"]?.toIntOrNull()?.takeIf { it > 0 } ?: 100
    val depth = 5
    val maxBranches = 5

    // Load graph once
    val graphResult = GetGraphStep.process(worldId)
    if (graphResult is Result.Failure) {
        respondBadRequest("Failed to load production graph")
        return
    }
    val graph = (graphResult as Result.Success).value

    // Load world productions once
    val worldProductionsResult = GetWorldProductionItemIdsStep.process(worldId)
    val worldProductions = (worldProductionsResult as? Result.Success)?.value ?: emptySet()

    // Load all gathering items for this project
    val gatheringResult = GetAllResourceGatheringItemsStep.process(projectId)
    if (gatheringResult is Result.Failure) {
        respondBadRequest("Failed to load resource gathering items")
        return
    }
    val gatheringItems = (gatheringResult as Result.Success).value

    val queries = ItemSourceGraphQueries(graph)
    var successCount = 0
    var skipCount = 0
    var failureCount = 0

    // Process each gathering item
    for (item in gatheringItems) {
        // Skip items that already have a confirmed path
        val existingPlan = LoadSavedPathStep.process(item.id)
        if (existingPlan is Result.Success) {
            val plan = existingPlan.value
            if (plan is Result.Success && plan.value.confirmed) {
                skipCount++
                continue
            }
        }

        val tree = queries
            .findProductionChain(item.itemId, depth)
            ?.deduplicated()
            ?.pruneRecursively(maxBranchesPerLevel = maxBranches)
            ?: continue

        if (tree.sources.isEmpty()) continue

        val context = SuggestionContext(
            requiredAmount = item.required,
            worldProductions = worldProductions,
            recipeThreshold = recipeThreshold
        )
        val suggestedPath = PathSuggestionService.suggestPath(tree, context)

        val upsertResult = UpsertPathStep.process(item.id to suggestedPath)
        if (upsertResult is Result.Failure) {
            logger.warn("Failed to save suggested path for gathering item {} ({}): {}", item.id, item.name, upsertResult.error)
            failureCount++
        } else {
            successCount++
        }
    }

    logger.info("Suggest-all for project {}: {} succeeded, {} skipped (confirmed), {} failed out of {} items",
        projectId, successCount, skipCount, failureCount, gatheringItems.size)

    clientRedirect("/app/worlds/$worldId/projects/$projectId")
}

