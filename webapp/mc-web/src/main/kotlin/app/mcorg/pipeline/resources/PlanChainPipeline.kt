package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanOverrides
import app.mcorg.engine.plan.PlanTarget
import app.mcorg.engine.plan.TargetTree
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.engine.plan.MemberPrior
import app.mcorg.pipeline.world.settings.general.UpdatePreferredWoodSpeciesStep
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.minecraft.GetItemSourceGraphForVersionStep
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.pipeline.resources.commonsteps.GetProgressForProjectStep
import app.mcorg.pipeline.task.SearchTasksInput
import app.mcorg.pipeline.task.SearchTasksStep
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.templated.dsl.pages.drillChainFragment
import app.mcorg.presentation.templated.dsl.pages.drillNotFoundFragment
import app.mcorg.presentation.templated.dsl.pages.gatheringPlannerFragment
import app.mcorg.presentation.templated.dsl.pages.nodePickerFragment
import app.mcorg.presentation.templated.dsl.pages.pickerNotFoundFragment
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.world.settings.general.versionGapsForPlan
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.utils.getUser
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
    val targetTree = plan.drillTreeFor(itemId)
    if (targetTree == null) {
        respondHtml(drillNotFoundFragment(project, "'$itemId' is not a target in this plan."))
        return
    }

    // Obtain the graph to compute candidate counts per node
    val graph = getGraphForWorld(worldId)

    // Build candidateCounts map: item-id → number of source candidates in the graph
    val candidateCounts = buildCandidateCounts(targetTree, graph)
    val nodeIngredients = buildNodeIngredients(plan)
    val overrides = GetPlanOverridesStep.process(projectId).getOrNull() ?: PlanOverrides.NONE

    respondHtml(drillChainFragment(project, targetTree, candidateCounts, nodeIngredients, overrides = overrides, graph = graph, highlightItemId = itemId))
}

/**
 * GET /worlds/{worldId}/projects/{projectId}/plan/chain/{itemId}/sources?node={encodedNodeId}
 *
 * Returns a picker fragment for one node in the drill chain. The picker shows all candidate
 * sources (for a multi-source item) or all tag members (for an OPEN_TAG node).
 *
 * Responds with an innerHTML fragment for `#picker-{nodeSlug}`.
 */
/**
 * Whether to offer "and use this wood everywhere" alongside a variant pick (MCO-487).
 *
 * Two conditions, and both are the server's to check rather than the client's to assert: the
 * world must not already have said which tree it farms — an offer to answer a settled question is
 * noise — and the user must be able to say it, since `preferred_wood_species` lives behind the
 * world-admin gate with the rest of world settings. A member who cannot set it sees the ordinary
 * per-tag picker, which is exactly what they had before.
 *
 * Re-checked on the pick as well as on the render, because the two are separate requests and only
 * the second one writes anything.
 */
private suspend fun ApplicationCall.worldWoodOfferable(worldId: Int): Boolean {
    val alreadySet = when (val r = GetPreferredWoodSpeciesStep.process(worldId)) {
        is Result.Success -> r.value != null
        is Result.Failure -> return false
    }
    if (alreadySet) return false
    return ValidateWorldMemberRole<Unit>(getUser(), Role.ADMIN, worldId).process(Unit) is Result.Success
}

/** A tag node's members, or empty for a node that is not an open tag. */
private fun tagMembersOf(node: TargetTree): List<MinecraftId> =
    (node.item as? MinecraftTag)?.content.orEmpty()

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

    // Optional search filter for high-fan-out pickers.
    val query = request.queryParameters["q"]?.takeIf { it.isNotBlank() }
    // MCO-487: decided here rather than by each caller, so the offer appears wherever the picker
    // does — the Next up widget, Needs attention, the drill — and cannot drift between them.
    val canOfferWorldWood = worldWoodOfferable(worldId)
    // "list" when the picker is opened inline from the List lens — resolutions then re-render
    // the list instead of the drill.
    val origin = request.queryParameters["origin"]?.takeIf { it.isNotBlank() }

    // Re-derive plan
    val plan = deriveOrNull(projectId, worldId) ?: run {
        respondHtml(pickerNotFoundFragment("Plan not available — try refreshing."))
        return
    }

    val targetTree = plan.drillTreeFor(targetItemId) ?: run {
        respondHtml(pickerNotFoundFragment("Target '$targetItemId' not found in plan."))
        return
    }

    val graph = getGraphForWorld(worldId)

    // Load current overrides so we can highlight the active selection
    val overrides = when (val r = GetPlanOverridesStep.process(projectId)) {
        is Result.Success -> r.value
        is Result.Failure -> null
    }

    // Primary: look up the node in the derived target tree.
    // Fallback: for a resolved tag (node absent from tree because redirectTag replaced it),
    // synthesize a TargetTree from the graph so the tag-member picker can still be opened.
    val node = findNodeById(targetTree, nodeId)
        ?: synthesizeTagNode(nodeId, graph)
        ?: run {
            respondHtml(pickerNotFoundFragment("Node '$nodeId' not found in chain."))
            return
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
            demand = node.quantityIfAlone,
            query = query,
            origin = origin,
            // Only a species set — "which tree" — is answerable world-wide. A form choice is
            // already settled by the selector, and colour or material sets are not wood.
            offerWorldWood = canOfferWorldWood && MemberPrior.isSpeciesChoice(tagMembersOf(node)),
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

    // MCO-506: what the planner had chosen for this node *before* the pin, so the row records
    // the disagreement and not just the answer. Read from the plan as it stands now — after the
    // write it is unrecoverable.
    val plannerPick = deriveOrNull(projectId, worldId)?.nodes?.get(nodeId)?.source?.getKey()

    // Persist the override
    when (val r = UpsertPlanOverrideStep(projectId).process(PlanOverride.Source(nodeId, sourceKey, plannerPick))) {
        is Result.Failure -> {
            defaultHandleError(r.error)
            return
        }
        is Result.Success -> { /* continue */ }
    }

    respondAfterOverride(worldId, projectId, targetItemId, params["origin"])
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

    // MCO-506: an open tag is the case where the planner declined to choose, so what is worth
    // recording is the member the picker ranked first — the answer the user would have got for
    // free. Equal to memberItemId means "agreed with the recommendation"; different means the
    // recommendation was wrong here, which is the whole signal.
    val plannerPick = recommendedMemberFor(projectId, worldId, nodeId)

    // Persist the override
    when (val r = UpsertPlanOverrideStep(projectId).process(PlanOverride.TagMember(nodeId, memberItemId, plannerPick))) {
        is Result.Failure -> {
            defaultHandleError(r.error)
            return
        }
        is Result.Success -> { /* continue */ }
    }

    // MCO-487: "and use it everywhere". An unchecked box submits nothing, so its absence is the
    // opt-out. The species comes from MemberPrior rather than a substring match here, because
    // `dark_oak_planks` contains "oak" and is not oak.
    if (params["setWorldWood"] != null && worldWoodOfferable(worldId)) {
        MemberPrior.speciesOf(memberItemId)?.let { species ->
            UpdatePreferredWoodSpeciesStep(worldId).process(species)
        }
    }

    respondAfterOverride(worldId, projectId, targetItemId, params["origin"])
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

    respondAfterOverride(worldId, projectId, targetItemId, request.queryParameters["origin"])
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
    val targetTree = plan?.drillTreeFor(targetItemId)

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
    val nodeIngredients = buildNodeIngredients(plan)
    val overrides = GetPlanOverridesStep.process(projectId).getOrNull() ?: PlanOverrides.NONE
    respondHtml(drillChainFragment(project, targetTree, candidateCounts, nodeIngredients, overrides = overrides, graph = graph, highlightItemId = targetItemId))
}

/**
 * After persisting an override, re-render whichever surface the user is on: the inline List
 * lens when the change came from there (origin=list), otherwise the full drill chain.
 */
private suspend fun ApplicationCall.respondAfterOverride(
    worldId: Int,
    projectId: Int,
    targetItemId: String,
    origin: String?,
) {
    if (origin == "list") respondListRerender(worldId, projectId)
    else respondDrillRerender(worldId, projectId, targetItemId)
}

/** Re-renders the List-lens fragment (#project-content) — used after an inline resolution. */
private suspend fun ApplicationCall.respondListRerender(worldId: Int, projectId: Int) {
    respondHtml(listRerenderFragment(worldId, projectId) ?: return)
}

/**
 * The List-lens fragment as a string, or null when the project could not be loaded (in which case
 * the error response has already been sent).
 *
 * Split out from [respondListRerender] so a caller that needs to say something *alongside* the
 * re-render — MCO-507's undo toast, swapped out-of-band — can concatenate rather than respond
 * twice.
 */
internal suspend fun ApplicationCall.listRerenderFragment(worldId: Int, projectId: Int): String? {
    val project = when (val r = GetProjectByIdStep.process(projectId)) {
        is Result.Success -> r.value
        is Result.Failure -> {
            defaultHandleError(r.error)
            return null
        }
    }
    val resources = GetAllResourceGatheringItemsStep.process(projectId).getOrNull() ?: emptyList()
    val tasks = SearchTasksStep(projectId).process(SearchTasksInput(completionStatus = "ALL")).getOrNull() ?: emptyList()
    val plan = deriveOrNull(projectId, worldId)
    val progressMap = GetProgressForProjectStep.process(projectId).getOrNull() ?: emptyMap()
    // Two different questions, deliberately two sources (MCO-461):
    //  - what the world already covers, for suppressing a suggestion (#417) — no threshold,
    //    because a farm that covers an item makes a second design for it pointless at any size
    //  - what this project *waits on*, for the prerequisite line — thresholded and
    //    cycle-ordered, because that is an ordering claim and shares the roadmap's rules
    val coveredByPlannedFarms = pendingFarmSuppliesFor(worldId, projectId, plan)
    val prerequisiteFarms = prerequisiteFarmsFor(worldId, projectId)

    // The same three the full page computes. This re-render is the one place they change for a
    // reason other than a page load: resolving an open tag turns a tag with no id anything can
    // produce into real demand a design may well cover (MCO-294), and it was the moment the
    // roll-up would otherwise silently fall back to the default threshold (MCO-401).
    val farmScaleThreshold = GetFarmScaleThresholdStep.process(worldId).getOrNull()
        ?: World.DEFAULT_FARM_SCALE_THRESHOLD
    val user = getUser()
    // MCO-407, and the same reason as the rest of this block: resolving a tag can turn a
    // dismissed item into new demand, and the roll-up and the suggestions must read the same
    // dismissal list when it does.
    val farmDismissals = farmDismissalsFor(worldId)
    val farmSuggestions = farmSuggestionsFor(
        plan, farmScaleThreshold, user.id, projectId, coveredByPlannedFarms,
        project.importedFromIdea?.first, farmDismissals.itemIds(),
    )
    val isAdmin = ValidateWorldMemberRole<Unit>(user, Role.ADMIN, worldId).process(Unit) is Result.Success

    return gatheringPlannerFragment(
        project, resources, tasks, plan, progressMap, prerequisiteFarms, farmScaleThreshold,
        farmSuggestions, versionGapsForPlan(projectId, plan), isAdmin, farmDismissals,
    )
}

/**
 * The member the picker would put first for [nodeId] — MCO-506's `planner_pick` for an open tag.
 *
 * An open tag is exactly the case where the planner declined to choose, so there is no "selected
 * source" to record. What is worth recording instead is the recommendation the user was looking at
 * when they answered: equal to their answer means they agreed with the ranking, different means the
 * ranking was wrong here.
 *
 * Resolved through the same node lookup and the same demand ([TargetTree.quantityIfAlone]) that
 * [handleGetNodePicker] renders from, so the recorded pick is the option the picker marked
 * "best score ★". Null when the plan or graph is unavailable, or the node is not a tag — never
 * guessed.
 */
internal suspend fun recommendedMemberFor(projectId: Int, worldId: Int, nodeId: String): String? {
    val graph = getGraphForWorld(worldId) ?: return null
    val plan = deriveOrNull(projectId, worldId) ?: return null
    val node = plan.drillTreeFor(nodeId)?.let { findNodeById(it, nodeId) }
        ?: synthesizeTagNode(nodeId, graph)
        ?: return null
    val tag = node.item as? MinecraftTag ?: return null
    return TagMemberRanking.recommended(graph, tag.content, node.quantityIfAlone)?.id
}

/**
 * Derives the gathering plan and returns it, or null on any failure (graceful).
 */
internal suspend fun deriveOrNull(projectId: Int, worldId: Int): GatheringPlan? =
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
 * Resolves the drill chain to show for [itemId]: its own per-target chain when it's a
 * defined target, otherwise the FULL chain of the first target that contains it (so a derived
 * intermediate like raw iron is shown in context — the chain above and below it — rather than
 * as an isolated node). The caller highlights [itemId] within the returned tree.
 */
internal fun GatheringPlan.drillTreeFor(itemId: String): TargetTree? =
    perTarget(itemId)
        ?: targets.firstNotNullOfOrNull { t -> perTarget(t.item.id)?.takeIf { findNodeById(it, itemId) != null } }

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

/**
 * Per-node ingredient summary ("5 Iron Ingot + 1 Chest", "1 Deepslate Iron Ore") keyed by
 * item id, shown in each drill node's hint so the indented children read as those inputs.
 * Built from the engine's own [PlanNode.requires] (per-craft quantities the planner used),
 * which is authoritative — the graph's raw input quantities can be inflated by data quirks.
 * Empty for nodes with no inputs (terminals, supplied, open tags).
 */
internal fun buildNodeIngredients(plan: GatheringPlan): Map<String, String> {
    fun nameOf(id: String): String = plan.nodes[id]?.item?.name ?: id
    fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    return plan.nodes.values
        .filter { it.requires.isNotEmpty() }
        .associate { node ->
            // Reduce inputs and the output count by their common factor so the recipe reads as
            // a clean ratio. Recipes that yield one output drop the "→ N" ("1 Chest + 5 Iron
            // Ingot"); multi-output recipes keep it so the ratio is exact, not a fraction
            // ("1 Oak Log → 4" instead of "0.25 Oak Log").
            val output = node.producedQuantity.toLong().coerceAtLeast(1)
            val divisor = node.requires
                .fold(output) { acc, r -> gcd(acc, r.quantityPerCraft.toLong()) }
                .coerceAtLeast(1)
            val inputs = node.requires
                .sortedBy { nameOf(it.itemId) }
                .joinToString(" + ") { "${it.quantityPerCraft / divisor} ${nameOf(it.itemId)}" }
            val reducedOutput = output / divisor
            node.item.id to if (reducedOutput > 1) "$inputs → $reducedOutput" else inputs
        }
}

/**
 * A reverse-provenance annotation for one material row: the capped [text] shown inline
 * ("Feeds 64 Stick · 40 Chest · 24 Birch Door · +1 more") plus, when the list was
 * truncated, the full uncapped list as a [title] tooltip so the hidden entries remain
 * discoverable on hover. [title] is null when nothing was truncated.
 */
data class FeedsLabel(val text: String, val title: String?)

/**
 * Reverse-provenance labels: item id → the end-item target(s) whose chain consumes this
 * material (from [GatheringPlan.feeders]). Each target renders as its name and its
 * net-of-collected required amount, ordered by amount desc then name, capped at [cap] with
 * a "+N more" tail (the full list surviving as a tooltip). Nodes that feed nothing (pure
 * targets) are absent from the map.
 */
internal fun buildFeedsLabels(plan: GatheringPlan, cap: Int = 3): Map<String, FeedsLabel> {
    val targetsById: Map<String, PlanTarget> = plan.targets.associateBy { it.item.id }
    fun render(targets: List<PlanTarget>) = "Feeds " + targets.joinToString(" · ") { "${it.amount} ${it.item.name}" }
    return plan.feeders.entries.mapNotNull { (nodeId, targetIds) ->
        val targets = targetIds
            .mapNotNull { targetsById[it] }
            .sortedWith(compareByDescending<PlanTarget> { it.amount }.thenBy { it.item.name })
        if (targets.isEmpty()) return@mapNotNull null
        if (targets.size <= cap) {
            nodeId to FeedsLabel(render(targets), null)
        } else {
            val text = render(targets.take(cap)) + " · +${targets.size - cap} more"
            nodeId to FeedsLabel(text, render(targets))
        }
    }.toMap()
}

/**
 * Synthesizes a [TargetTree] for a tag id that is absent from the derived target tree
 * (because [PlanOverrides.tagMember] redirected it to a concrete member). Only succeeds
 * when the id starts with `#` (i.e. it is a tag id) and the graph contains the tag's
 * item node — which carries the [MinecraftTag] with its full member list. Returns null
 * when the graph is unavailable or the tag is not found.
 *
 * The synthesized node has status [PlanNodeStatus.OPEN_TAG] so [nodePickerFragment]
 * opens the tag-member variant of the picker. Quantities are 0 (the tag itself is not in
 * the derived plan — the member item holds the real demand). The active override is read
 * from [overrides] so the picker can highlight the current choice.
 */
internal fun synthesizeTagNode(
    nodeId: String,
    graph: ItemSourceGraph?,
): TargetTree? {
    if (graph == null || !nodeId.startsWith("#")) return null
    val tagItemNode = graph.getItemNodesByStringId(nodeId).firstOrNull() ?: return null
    val tag = tagItemNode.item as? MinecraftTag ?: return null
    return TargetTree(
        item = tag,
        quantityIfAlone = 0L,
        craftsIfAlone = 0L,
        status = PlanNodeStatus.OPEN_TAG,
        source = null,
    )
}

/** Builds a minimal validation error HTML string for respondBadRequest. */
internal fun buildValidationError(param: String, message: String): String =
    "<p class=\"validation-error-message\" id=\"validation-error-$param\">$message</p>"
