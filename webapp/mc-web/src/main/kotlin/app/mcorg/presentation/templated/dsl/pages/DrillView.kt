package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.project.Project
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode
import app.mcorg.engine.plan.PlanNodeStatus
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
 */
fun drillChainFragment(
    project: Project,
    target: TargetTree,
    candidateCounts: Map<String, Int>,
): String = createHTML().div {
    id = "project-content"

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
        renderTargetTree(target, candidateCounts, depth = 0, worldId = worldId, projectId = projectId, encodedTargetId = encodedTargetId)
    }
}

/** Renders a [TargetTree] node and recurses into children. */
private fun DIV.renderTargetTree(
    node: TargetTree,
    candidateCounts: Map<String, Int>,
    depth: Int,
    worldId: Int,
    projectId: Int,
    encodedTargetId: String,
) {
    val depthClass = "chain-node--depth-${depth.coerceAtMost(4)}"
    val isForced = isForced(node, candidateCounts)
    val isMultiSource = isMultiSource(node, candidateCounts)
    val nodeSlug = node.item.id.replace(Regex("[^a-zA-Z0-9]"), "-")
    val encodedNodeId = encodeId(node.item.id)
    val pickerSlotId = "picker-$nodeSlug"
    val pickerUrl = "/worlds/$worldId/projects/$projectId/plan/chain/$encodedTargetId/sources?node=$encodedNodeId"

    div("chain-node $depthClass") {
        // Item name + optional method hint for RESOLVED nodes
        span("chain-node__name") {
            +node.item.name
            val methodHint = node.source?.getMethodLabel()
            if (methodHint != null && (node.status == PlanNodeStatus.RESOLVED || node.status == PlanNodeStatus.RAW_GATHER)) {
                span("chain-node__method") { +"· $methodHint" }
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
        renderTargetTree(child, candidateCounts, depth + 1, worldId, projectId, encodedTargetId)
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
): String {
    val encodedTargetId = encodeId(targetItemId)
    val encodedNodeId = encodeId(node.item.id)
    val baseUrl = "/worlds/$worldId/projects/$projectId/plan/chain/$encodedTargetId"
    val clearUrl = "$baseUrl/override?node=$encodedNodeId"
    val isTag = node.status == PlanNodeStatus.OPEN_TAG && node.item is MinecraftTag

    return createHTML().div("picker") {
        if (isTag) {
            // Tag-member picker
            val tag = node.item as MinecraftTag
            val allMembers = tag.content.sortedBy { it.name }
            val displayedMembers = allMembers.take(PICKER_MAX_OPTIONS)
            val truncated = allMembers.size > PICKER_MAX_OPTIONS

            span("section-label picker__label") { +"Pick a variant" }

            div("stack--xs") {
                for (member in displayedMembers) {
                    val isSelected = member.id == activeMemberId
                    div("picker-opt${if (isSelected) " picker-opt--sel" else ""}") {
                        attributes["hx-post"] = "$baseUrl/tag"
                        attributes["hx-vals"] = """{"node":"${node.item.id}","memberItemId":"${member.id}"}"""
                        attributes["hx-target"] = "#project-content"
                        attributes["hx-swap"] = "outerHTML"
                        span("picker-opt__name") { +member.name }
                        if (isSelected) {
                            span("picker-opt__hint") { +"selected" }
                        }
                    }
                }
            }

            if (truncated) {
                p("picker__overflow-note") {
                    +"Showing $PICKER_MAX_OPTIONS of ${allMembers.size} · ranked picks + search coming soon"
                }
            }
        } else {
            // Source picker
            val candidates: List<SourceNode> = graph
                ?.getSourcesForItem(node.item)
                ?.sortedBy { it.getName() }
                ?: emptyList()
            val displayed = candidates.take(PICKER_MAX_OPTIONS)
            val truncated = candidates.size > PICKER_MAX_OPTIONS

            span("section-label picker__label") { +"Choose source" }

            if (displayed.isEmpty()) {
                p("picker__empty") { +"No sources available for this item." }
            } else {
                div("stack--xs") {
                    for (source in displayed) {
                        val isSelected = source.getKey() == activeSourceKey
                        div("picker-opt${if (isSelected) " picker-opt--sel" else ""}") {
                            attributes["hx-post"] = "$baseUrl/pin"
                            attributes["hx-vals"] = """{"node":"${node.item.id}","sourceKey":"${source.getKey()}"}"""
                            attributes["hx-target"] = "#project-content"
                            attributes["hx-swap"] = "outerHTML"
                            span("picker-opt__name") { +source.getName() }
                            span("picker-opt__hint") { +source.getMethodLabel() }
                        }
                    }
                }
            }

            if (truncated) {
                p("picker__overflow-note") {
                    +"Showing $PICKER_MAX_OPTIONS of ${candidates.size} · ranked picks + search coming soon"
                }
            }
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
 * Graceful fallback fragment for the picker slot when node/plan lookup fails.
 * Rendered into `#picker-{nodeSlug}` via innerHTML.
 */
fun pickerNotFoundFragment(reason: String): String = createHTML().p("picker__empty") { +reason }

// ---------------------------------------------------------------------------
// Private utilities
// ---------------------------------------------------------------------------

/** URL-encodes an item or tag id for use in URL path segments. */
private fun encodeId(id: String): String =
    URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20")
