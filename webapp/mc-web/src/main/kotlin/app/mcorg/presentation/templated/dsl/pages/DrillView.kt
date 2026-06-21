package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.project.Project
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.TargetTree
import kotlinx.html.*
import kotlinx.html.stream.createHTML

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
        renderTargetTree(target, candidateCounts, depth = 0)
    }
}

/** Renders a [TargetTree] node and recurses into children. */
private fun DIV.renderTargetTree(
    node: TargetTree,
    candidateCounts: Map<String, Int>,
    depth: Int,
) {
    val depthClass = "chain-node--depth-${depth.coerceAtMost(4)}"
    val isForced = isForced(node, candidateCounts)
    val isMultiSource = isMultiSource(node, candidateCounts)

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
                // Multi-source or open tag: render ⇄ chip (Phase 1: no-op placeholder)
                val nodeSource = node.source  // local val to enable smart cast across module boundary
                val chipLabel = when {
                    nodeSource != null -> nodeSource.getName()
                    node.status == PlanNodeStatus.OPEN_TAG -> "Pick variant"
                    else -> "Pick source"
                }
                button(classes = "chip") {
                    type = ButtonType.button
                    // Phase 1: no hx-* attributes — placeholder only
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

    // Recurse into children
    for (child in node.children) {
        renderTargetTree(child, candidateCounts, depth + 1)
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
