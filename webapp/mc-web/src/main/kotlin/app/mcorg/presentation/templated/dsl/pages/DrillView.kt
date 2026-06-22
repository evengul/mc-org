package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.project.Project
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanOverrides
import app.mcorg.engine.plan.SourceRanking
import app.mcorg.engine.plan.TargetTree
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// High fan-out cap: when a node has more than this many candidates we only show the first N.
private const val PICKER_MAX_OPTIONS = 30

/**
 * Drill-chain view fragment for one plan target.
 *
 * Rendered into `#project-content` via an `outerHTML` swap, exactly like the lens fragments.
 *
 * @param project the owning project (for URLs and titles).
 * @param target the resolved [TargetTree] for the drill target.
 * @param candidateCounts item-id → number of source candidates in the graph. Used to decide
 *   whether a node is "forced" (≤ 1 candidate) or "multi-source" (≥ 2 candidates).
 * @param overrides the current project overrides — when non-empty, the "Active choices" panel
 *   is rendered below the chain so resolved tags and pinned sources remain reachable.
 * @param graph the live item-source graph — used by the panel to resolve item names for labels.
 */
fun drillChainFragment(
    project: Project,
    target: TargetTree,
    candidateCounts: Map<String, Int>,
    nodeIngredients: Map<String, String> = emptyMap(),
    overrides: PlanOverrides = PlanOverrides.NONE,
    graph: ItemSourceGraph? = null,
    highlightItemId: String? = null,
): String = createHTML().div {
    id = "project-content"
    drillChainContent(project, target, candidateCounts, nodeIngredients, overrides, graph, highlightItemId)
}

/**
 * The drill body (back-header + chain + active choices panel), rendered into the caller's flow.
 * Shared by [drillChainFragment] (the HTMX swap response) and the full page shell, so that a
 * `?drill=` page load renders the same drill inside #project-content.
 *
 * @param nodeIngredients item-id → its source's ingredient summary ("5 Iron Ingot + 1 Chest"),
 *   shown in each node's hint so the indented children read as those exact inputs.
 * @param overrides the current project overrides — when non-empty, renders the "Active choices"
 *   panel below the chain so resolved tags and pinned sources remain reachable.
 * @param graph the live item-source graph — used by the panel to resolve item names for labels.
 */
fun FlowContent.drillChainContent(
    project: Project,
    target: TargetTree,
    candidateCounts: Map<String, Int>,
    nodeIngredients: Map<String, String> = emptyMap(),
    overrides: PlanOverrides = PlanOverrides.NONE,
    graph: ItemSourceGraph? = null,
    highlightItemId: String? = null,
) {
    val worldId = project.worldId
    val projectId = project.id
    val listFragmentUrl = "/worlds/$worldId/projects/$projectId/detail-content?lens=list"
    val listPageUrl = "/worlds/$worldId/projects/$projectId?lens=list"
    val targetName = target.item.name
    val encodedTargetId = encodeId(target.item.id)

    div("drill-header") {
        button(classes = "btn btn--ghost btn--sm") {
            attributes["hx-get"] = listFragmentUrl
            attributes["hx-target"] = "#project-content"
            attributes["hx-swap"] = "outerHTML"
            attributes["hx-push-url"] = listPageUrl
            +"< Back to plan"
        }
        span("section-label") {
            +"$targetName · chain — if gathered alone · tap ⇄ to re-pin"
        }
    }

    div("drill-chain") {
        renderTargetTree(target, candidateCounts, nodeIngredients, highlightItemId, depth = 0, worldId = worldId, projectId = projectId, encodedTargetId = encodedTargetId)
    }

    activeChoicesPanel(worldId, projectId, encodedTargetId, overrides, graph)
}

/** Renders a [TargetTree] node and recurses into children. */
private fun DIV.renderTargetTree(
    node: TargetTree,
    candidateCounts: Map<String, Int>,
    nodeIngredients: Map<String, String>,
    highlightItemId: String?,
    depth: Int,
    worldId: Int,
    projectId: Int,
    encodedTargetId: String,
) {
    val depthClass = "chain-node--depth-${depth.coerceAtMost(4)}"
    val highlightClass = if (node.item.id == highlightItemId) " chain-node--current" else ""
    val isForced = isForced(node, candidateCounts)
    val isMultiSource = isMultiSource(node, candidateCounts)
    val nodeSlug = node.item.id.replace(Regex("[^a-zA-Z0-9]"), "-")
    val encodedNodeId = encodeId(node.item.id)
    val pickerSlotId = "picker-$nodeSlug"
    val pickerUrl = "/worlds/$worldId/projects/$projectId/plan/chain/$encodedTargetId/sources?node=$encodedNodeId"

    div("chain-node $depthClass$highlightClass") {
        // Item name + optional method hint for RESOLVED nodes
        span("chain-node__name") {
            +node.item.name
            // Hint = method + ingredient summary ("Smelting · 1 Deepslate Iron Ore"), so the
            // node states what it consumes and the indented children read as those inputs.
            val methodHint = node.source?.getMethodLabel()
                ?.takeIf { node.status == PlanNodeStatus.RESOLVED || node.status == PlanNodeStatus.RAW_GATHER }
            val hint = listOfNotNull(methodHint, nodeIngredients[node.item.id]).joinToString(" · ")
            if (hint.isNotEmpty()) {
                span("chain-node__method") { +"· $hint" }
            }
        }

        // Quantity if alone
        span("chain-node__qty") { +node.quantityIfAlone.toString() }

        // Source control: forced label or ⇄ chip
        when {
            node.status == PlanNodeStatus.SUPPLIED -> {
                val supplyLabel = node.supply?.label ?: "Supplied"
                span("chain-node__forced") { +"Supplied · $supplyLabel" }
            }
            node.status == PlanNodeStatus.BLOCKED -> {
                span("chain-node__forced") { +"Blocked — no source" }
            }
            node.status == PlanNodeStatus.OPEN_TAG || isMultiSource -> {
                // Multi-source or open tag: render ⇄ chip that loads the picker
                val nodeSource = node.source
                val chipLabel = when {
                    nodeSource != null -> nodeSource.getName()
                    node.status == PlanNodeStatus.OPEN_TAG -> "Pick variant"
                    else -> "Pick source"
                }
                button(classes = "chip") {
                    type = ButtonType.button
                    attributes["hx-get"] = pickerUrl
                    attributes["hx-target"] = "#$pickerSlotId"
                    attributes["hx-swap"] = "innerHTML"
                    +"⇄ $chipLabel"
                }
            }
            isForced -> {
                val methodLabel = node.source?.getMethodLabel() ?: "gather"
                span("chain-node__forced") { +"$methodLabel · 1 way" }
            }
            else -> {
                // Terminal with no source info (RAW_GATHER with no graph source)
                val methodLabel = node.source?.getMethodLabel() ?: "gather"
                span("chain-node__forced") { +"$methodLabel · 1 way" }
            }
        }
    }

    // Picker slot: only rendered for chip nodes; starts empty, filled by hx-get
    if (node.status == PlanNodeStatus.OPEN_TAG || isMultiSource) {
        div("chain-node__picker") {
            id = pickerSlotId
            // empty — filled by HTMX on chip click
        }
    }

    // Recurse into children
    for (child in node.children) {
        renderTargetTree(child, candidateCounts, nodeIngredients, highlightItemId, depth + 1, worldId, projectId, encodedTargetId)
    }
}

/**
 * A node is "forced" (no chip) when there is at most one candidate source in the graph,
 * OR the node is RESOLVED/RAW_GATHER and the item is a terminal (no children) with only 1 way.
 */
private fun isForced(node: TargetTree, candidateCounts: Map<String, Int>): Boolean {
    if (node.status == PlanNodeStatus.OPEN_TAG || node.status == PlanNodeStatus.SUPPLIED || node.status == PlanNodeStatus.BLOCKED) {
        return false
    }
    val count = candidateCounts[node.item.id] ?: 0
    return count <= 1
}

private fun isMultiSource(node: TargetTree, candidateCounts: Map<String, Int>): Boolean {
    if (node.status == PlanNodeStatus.OPEN_TAG) return true
    if (node.status == PlanNodeStatus.SUPPLIED || node.status == PlanNodeStatus.BLOCKED) return false
    val count = candidateCounts[node.item.id] ?: 0
    return count >= 2
}

/**
 * Graceful fallback fragment when the plan cannot be derived or the item is not a target.
 * Renders into `#project-content` so the outerHTML swap still replaces the right element.
 */
fun drillNotFoundFragment(project: Project, reason: String): String = createHTML().div {
    id = "project-content"

    val worldId = project.worldId
    val projectId = project.id
    val listFragmentUrl = "/worlds/$worldId/projects/$projectId/detail-content?lens=list"
    val listPageUrl = "/worlds/$worldId/projects/$projectId?lens=list"

    div("drill-header") {
        button(classes = "btn btn--ghost btn--sm") {
            attributes["hx-get"] = listFragmentUrl
            attributes["hx-target"] = "#project-content"
            attributes["hx-swap"] = "outerHTML"
            attributes["hx-push-url"] = listPageUrl
            +"< Back to plan"
        }
    }

    div("callout callout--info") {
        span("callout__icon") { +"i" }
        div("callout__body") { +reason }
    }
}

/**
 * Picker fragment for one node — source selector or tag-member selector.
 *
 * Rendered into `#picker-{nodeSlug}` via innerHTML swap.
 *
 * For source nodes (multi-source items): shows all candidate sources sorted by getName(),
 * capped at [PICKER_MAX_OPTIONS] with a note when truncated. Each option POSTs to the
 * `/pin` endpoint on click.
 *
 * For tag nodes (OPEN_TAG): shows all member items from the tag's .content list, sorted
 * by name, same cap. Each option POSTs to the `/tag` endpoint.
 *
 * When an override is currently active, a "Clear override" control is shown.
 *
 * @param worldId  world id
 * @param projectId  project id
 * @param targetItemId  the drill target's item id (URL-encoded in HTMX attrs)
 * @param node  the TargetTree node whose picker to show
 * @param graph  the live item-source graph (may be null — renders graceful empty state)
 * @param activeSourceKey  the currently active source override key, if any
 * @param activeMemberId  the currently active tag-member override id, if any
 */
fun nodePickerFragment(
    worldId: Int,
    projectId: Int,
    targetItemId: String,
    node: TargetTree,
    graph: ItemSourceGraph?,
    activeSourceKey: String?,
    activeMemberId: String?,
    demand: Long,
    query: String? = null,
    origin: String? = null,
): String {
    val encodedTargetId = encodeId(targetItemId)
    val encodedNodeId = encodeId(node.item.id)
    val baseUrl = "/worlds/$worldId/projects/$projectId/plan/chain/$encodedTargetId"
    // Carry origin=list through so a resolution made inline from the List lens re-renders
    // the list, not the drill.
    val originQuery = if (origin != null) "&origin=$origin" else ""
    val originJson = if (origin != null) ""","origin":"$origin"""" else ""
    val clearUrl = "$baseUrl/override?node=$encodedNodeId$originQuery"
    val sourcesUrl = "$baseUrl/sources?node=$encodedNodeId$originQuery"
    val pickerSlotId = "picker-${node.item.id.replace(Regex("[^a-zA-Z0-9]"), "-")}"
    val isTag = node.status == PlanNodeStatus.OPEN_TAG && node.item is MinecraftTag
    val q = query?.trim().orEmpty()

    return createHTML().div("picker") {
        if (isTag) {
            // Tag-member picker — members ranked by the score of their best source.
            val tag = node.item as MinecraftTag
            val ranked = tag.content
                .map { member ->
                    val best = graph?.let { SourceRanking.rankSources(it, member, demand).firstOrNull() }
                    RankedMember(member, best?.source, best?.score ?: Int.MIN_VALUE)
                }
                .sortedWith(compareByDescending<RankedMember> { it.score }.thenBy { it.member.name })
            val topId = ranked.firstOrNull()?.member?.id
            val filtered = if (q.isEmpty()) ranked else ranked.filter { it.member.name.contains(q, ignoreCase = true) }
            val displayed = filtered.take(PICKER_MAX_OPTIONS)

            span("section-label picker__label") { +"Pick a variant" }
            if (ranked.size > PICKER_MAX_OPTIONS) pickerSearch(sourcesUrl, pickerSlotId, q)

            if (displayed.isEmpty()) {
                p("picker__empty") { +(if (q.isEmpty()) "No variants available." else "No variants match \"$q\".") }
            } else {
                div("stack--xs") {
                    for (rm in displayed) {
                        val isSelected = rm.member.id == activeMemberId
                        div("picker-opt${if (isSelected) " picker-opt--sel" else ""}") {
                            attributes["hx-post"] = "$baseUrl/tag"
                            attributes["hx-vals"] = """{"node":"${node.item.id}","memberItemId":"${rm.member.id}"$originJson}"""
                            attributes["hx-target"] = "#project-content"
                            attributes["hx-swap"] = "outerHTML"
                            span("picker-opt__name") { +rm.member.name }
                            pickerOptHint(rm.bestSource?.getMethodLabel(), isBest = rm.member.id == topId, isSelected = isSelected)
                        }
                    }
                }
            }
            overflowNote(displayed.size, filtered.size, q)
        } else {
            // Source picker — candidates ranked by SelectionScorer (read-only reuse).
            val ranked = graph?.let { SourceRanking.rankSources(it, node.item, demand) } ?: emptyList()
            val filtered = if (q.isEmpty()) ranked else ranked.filter { sourceLabel(it.source, graph).contains(q, ignoreCase = true) }
            val displayed = filtered.take(PICKER_MAX_OPTIONS)

            span("section-label picker__label") { +"Choose source" }
            if (ranked.size > PICKER_MAX_OPTIONS) pickerSearch(sourcesUrl, pickerSlotId, q)

            if (displayed.isEmpty()) {
                p("picker__empty") {
                    +(if (ranked.isEmpty()) "No sources available for this item." else "No sources match \"$q\".")
                }
            } else {
                div("stack--xs") {
                    for (rs in displayed) {
                        val source = rs.source
                        val isSelected = source.getKey() == activeSourceKey
                        div("picker-opt${if (isSelected) " picker-opt--sel" else ""}") {
                            attributes["hx-post"] = "$baseUrl/pin"
                            attributes["hx-vals"] = """{"node":"${node.item.id}","sourceKey":"${source.getKey()}"$originJson}"""
                            attributes["hx-target"] = "#project-content"
                            attributes["hx-swap"] = "outerHTML"
                            span("picker-opt__name") { +sourceLabel(source, graph) }
                            pickerOptHint(source.getMethodLabel(), isBest = rs.rank == 0, isSelected = isSelected)
                        }
                    }
                }
            }
            overflowNote(displayed.size, filtered.size, q)
        }

        // Clear override control — shown when an override is currently active
        val hasOverride = activeSourceKey != null || activeMemberId != null
        if (hasOverride) {
            button(classes = "btn btn--ghost btn--sm picker__clear") {
                type = ButtonType.button
                attributes["hx-delete"] = clearUrl
                attributes["hx-target"] = "#project-content"
                attributes["hx-swap"] = "outerHTML"
                +"Clear override"
            }
        }
    }
}

/**
 * A distinguishing label for a source option. [SourceNode.getName] already carries the
 * pretty filename for block/entity sources, but for recipes and chest loot it collapses to
 * the bare type ("Crafting"/"Smelting"/"Chest"), making siblings indistinguishable. When the
 * graph exposes ingredients, name the source by them ("from Iron Ore", "from Block of Iron").
 */
private fun sourceLabel(source: SourceNode, graph: ItemSourceGraph?): String {
    val inputs = graph?.getRequiredItems(source)?.map { it.item.name }?.sorted().orEmpty()
    if (inputs.isNotEmpty()) return "from ${inputs.joinToString(" + ")}"
    return lootTableName(source) ?: source.getName()
}

/**
 * The looting location for a loot source, from its loot-table file ("Desert pyramid",
 * "Simple dungeon"), or null for non-loot sources. getName() enriches block/entity loot with
 * their file already; recipes carry ingredients — only the bare-typed loot tables (chest,
 * gift, archaeology, barter) need this, and the distinguishing info is in `filename`.
 */
internal fun lootTableName(source: SourceNode): String? {
    if (!source.sourceType.isLoot() || source.getName() != source.sourceType.name) return null
    val pretty = source.filename.substringAfterLast('/').substringBeforeLast('.')
        .replace('_', ' ').trim()
        .replaceFirstChar { it.uppercaseChar() }
    return pretty.ifBlank { null }
}

/** A tag member paired with its best source + that source's score, for ranking. */
private data class RankedMember(val member: MinecraftId, val bestSource: SourceNode?, val score: Int)

/** The per-option hint line: method label, a "best score ★" marker, and/or "selected". */
private fun FlowContent.pickerOptHint(methodLabel: String?, isBest: Boolean, isSelected: Boolean) {
    val parts = listOfNotNull(
        methodLabel,
        if (isBest) "best score ★" else null,
        if (isSelected) "selected" else null,
    )
    if (parts.isNotEmpty()) span("picker-opt__hint") { +parts.joinToString(" · ") }
}

/** A search box that re-fetches the picker filtered by name (high fan-out nodes only). */
private fun FlowContent.pickerSearch(sourcesUrl: String, pickerSlotId: String, q: String) {
    div("picker-search") {
        input(type = InputType.search, classes = "form-control picker-search__input") {
            name = "q"
            placeholder = "Search…"
            value = q
            attributes["hx-get"] = sourcesUrl
            attributes["hx-trigger"] = "keyup changed delay:300ms"
            attributes["hx-target"] = "#$pickerSlotId"
            attributes["hx-swap"] = "innerHTML"
            attributes["hx-vals"] = "js:{q: this.value}"
        }
    }
}

/** "Showing N of M" note when the (filtered) list is capped. */
private fun FlowContent.overflowNote(shown: Int, matching: Int, q: String) {
    if (matching > shown) {
        p("picker__overflow-note") {
            +("Showing $shown of $matching" + if (q.isEmpty()) " · search to narrow" else "")
        }
    }
}

/**
 * Graceful fallback fragment for the picker slot when node/plan lookup fails.
 * Rendered into `#picker-{nodeSlug}` via innerHTML.
 */
fun pickerNotFoundFragment(reason: String): String = createHTML().p("picker__empty") { +reason }

// ---------------------------------------------------------------------------
// Active choices panel
// ---------------------------------------------------------------------------

/**
 * "Active choices" panel: lists every active override (source pins and tag-member resolutions)
 * so the user can edit or clear any of them — even when the overridden node is absent from the
 * current derived tree (e.g. a resolved tag whose node was replaced by the concrete member).
 *
 * Renders nothing when there are no overrides.
 *
 * Each row shows a human-readable label and two controls:
 * - **Edit**: reopens the source/tag picker in a slot inside the row (hx-get to /sources).
 * - **Clear**: calls DELETE /override → re-renders #project-content (outerHTML).
 *
 * The picker slot per row uses id `panel-picker-{slug}` to avoid collisions with the
 * `picker-{slug}` slots that live inside the chain itself.
 *
 * @param worldId world id
 * @param projectId project id
 * @param encodedTargetId URL-encoded id of the drill target (used in endpoint URLs)
 * @param overrides the current set of active overrides
 * @param graph the live item-source graph — used to resolve item/tag names for labels
 */
fun FlowContent.activeChoicesPanel(
    worldId: Int,
    projectId: Int,
    encodedTargetId: String,
    overrides: PlanOverrides,
    graph: ItemSourceGraph?,
) {
    val hasSources = overrides.sourceByItem.isNotEmpty()
    val hasTagMembers = overrides.tagMember.isNotEmpty()
    if (!hasSources && !hasTagMembers) return

    div("active-choices") {
        id = "active-choices"
        span("section-label active-choices__label") { +"Active choices" }

        div("active-choices__list") {
            // Source pins
            for ((itemId, sourceKey) in overrides.sourceByItem) {
                val itemName = resolveItemName(itemId, graph)
                val sourceMethod = trySourceMethodLabel(sourceKey)
                val label = "$itemName — $sourceMethod"
                val slug = itemId.replace(Regex("[^a-zA-Z0-9]"), "-")
                val encodedNodeId = encodeId(itemId)
                val pickerSlotId = "panel-picker-$slug"
                val pickerUrl = "/worlds/$worldId/projects/$projectId/plan/chain/$encodedTargetId/sources?node=$encodedNodeId"
                val clearUrl = "/worlds/$worldId/projects/$projectId/plan/chain/$encodedTargetId/override?node=$encodedNodeId"

                activeChoiceRow(label, pickerSlotId, pickerUrl, clearUrl)
            }

            // Tag member resolutions
            for ((tagId, memberItemId) in overrides.tagMember) {
                val tagName = resolveItemName(tagId, graph)
                val memberName = resolveItemName(memberItemId, graph)
                val label = "$tagName → $memberName"
                val slug = tagId.replace(Regex("[^a-zA-Z0-9]"), "-")
                val encodedNodeId = encodeId(tagId)
                val pickerSlotId = "panel-picker-$slug"
                val pickerUrl = "/worlds/$worldId/projects/$projectId/plan/chain/$encodedTargetId/sources?node=$encodedNodeId"
                val clearUrl = "/worlds/$worldId/projects/$projectId/plan/chain/$encodedTargetId/override?node=$encodedNodeId"

                activeChoiceRow(label, pickerSlotId, pickerUrl, clearUrl)
            }
        }
    }
}

/** One row of the active-choices panel. */
private fun FlowContent.activeChoiceRow(
    label: String,
    pickerSlotId: String,
    pickerUrl: String,
    clearUrl: String,
) {
    div("active-choice") {
        span("active-choice__label") { +label }
        div("active-choice__actions") {
            button(classes = "btn btn--ghost btn--sm active-choice__edit") {
                type = ButtonType.button
                attributes["hx-get"] = pickerUrl
                attributes["hx-target"] = "#$pickerSlotId"
                attributes["hx-swap"] = "innerHTML"
                +"⇄ Edit"
            }
            button(classes = "btn btn--ghost btn--sm active-choice__clear") {
                type = ButtonType.button
                attributes["hx-delete"] = clearUrl
                attributes["hx-target"] = "#project-content"
                attributes["hx-swap"] = "outerHTML"
                +"× Clear"
            }
        }
        // Picker slot for this row — filled by hx-get on Edit click
        div("chain-node__picker") {
            id = pickerSlotId
        }
    }
}

/**
 * Resolves a human-readable item/tag name from [graph]. Falls back to prettifying
 * the id when the graph is null or the id is not found (test environment, no graph).
 */
private fun resolveItemName(id: String, graph: ItemSourceGraph?): String {
    if (graph != null) {
        val node = graph.getItemNodesByStringId(id).firstOrNull()
        if (node != null) return node.item.name
    }
    // Fallback: strip namespace prefix and prettify ("minecraft:oak_planks" → "Oak Planks")
    val bare = id.removePrefix("#").substringAfter(":")
    return bare.replace('_', ' ').trim().replaceFirstChar { it.uppercaseChar() }
}

/**
 * Returns the method label for a source key ("Break Block", "Crafting", etc.),
 * or a prettified fallback when parsing fails.
 */
private fun trySourceMethodLabel(sourceKey: String): String = try {
    SourceNode.fromKey(sourceKey).getMethodLabel()
} catch (_: Exception) {
    sourceKey.substringAfterLast(':').replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
}

// ---------------------------------------------------------------------------
// Private utilities
// ---------------------------------------------------------------------------

/** URL-encodes an item or tag id for use in URL path segments. */
private fun encodeId(id: String): String =
    URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20")
