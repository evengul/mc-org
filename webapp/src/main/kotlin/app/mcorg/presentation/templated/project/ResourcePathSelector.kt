package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.resources.ProductionPath
import app.mcorg.domain.model.resources.SourceNode
import app.mcorg.domain.services.ProductionBranch
import app.mcorg.domain.services.ProductionTree
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.button.ghostButton
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.LI
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.ol
import kotlinx.html.onClick
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul

/**
 * Main path selector tree component
 */
fun DIV.pathSelectorTree(
    worldId: Int,
    projectId: Int,
    gatheringId: Int,
    tree: ProductionTree,
    selectedPath: ProductionPath?,
    depth: Int,
    maxBranches: Int
) {
    div("path-selector") {
        id = "path-selector-$gatheringId"

        // Breadcrumb navigation
        pathBreadcrumb(tree, selectedPath)

        // Main tree view
        div("path-selector__tree") {
            h3 {
                +"Select how to obtain ${tree.targetItem.itemId.substringAfter("minecraft:")}"
            }

            if (tree.sources.isEmpty()) {
                p("subtle") {
                    +"This is a base resource - find it in the world!"
                }
            } else {
                ul("path-selector__branches") {
                    tree.sources.sortedByDescending { it.getScore() }.take(maxBranches).forEach { branch ->
                        li {
                            pathNode(worldId, projectId, gatheringId, tree.targetItem.itemId, branch, selectedPath, depth, maxBranches)
                        }
                    }
                }

                if (tree.sources.size > maxBranches) {
                    p("subtle path-selector__more-options") {
                        +"${tree.sources.size - maxBranches} more option(s) available. Adjust maxBranches to see more."
                    }
                }
            }
        }

        // Selection summary panel
        pathSummary(worldId, projectId, gatheringId, selectedPath)
    }
}

/**
 * Single collapsible node in the tree
 */
fun LI.pathNode(
    worldId: Int,
    projectId: Int,
    gatheringId: Int,
    itemId: String,
    branch: ProductionBranch,
    selectedPath: ProductionPath?,
    depth: Int,
    maxBranches: Int
) {
    val isExpanded = branch.requiredItems.isNotEmpty()
    val isSelected = isSourceSelectedForItem(selectedPath, itemId, branch.source.getKey())

    classes = setOf(
        "path-node",
        if (isExpanded) "path-node--expanded" else "path-node--collapsed",
        if (isSelected) "path-node--selected" else ""
    ).filter { it.isNotEmpty() }.toSet()

    div("path-node__header") {
        button(classes = "path-node__button") {
            type = ButtonType.button
            // Click handler to select this path
            onClick = "window.selectPathNode('$gatheringId', '$itemId', '${branch.source.getKey()}')"

            // Icon showing source type
            span("path-node__icon") {
                +getSourceIcon(branch.source.sourceType.id)
            }

            // Source name
            span("path-node__name") {
                +branch.source.getName()
            }

            // Requirements count
            if (branch.requiredItems.isNotEmpty()) {
                span("path-node__count badge") {
                    +"${branch.requiredItems.size} ${if (branch.requiredItems.size == 1) "requirement" else "requirements"}"
                }
            }

            // Selection indicator
            if (isSelected) {
                span("path-node__check") {
                    +"✓"
                }
            }
        }
    }

    // Expandable requirements section
    if (branch.requiredItems.isNotEmpty()) {
        div("path-node__requirements") {
            ul("path-selector__branches") {
                branch.requiredItems.forEach { requiredTree ->
                    li("path-requirement") {
                        div("path-requirement__header") {
                            span {
                                + requiredTree.targetItem.itemId.substringAfter("minecraft:")
                            }

                            if (requiredTree.sources.isEmpty()) {
                                span("badge badge--success") {
                                    +"Base resource"
                                }
                            } else {
                                val currentPathEncoded = selectedPath?.encode() ?: ""
                                ghostButton(selectedPath?.takeIf { it.itemId == requiredTree.targetItem.itemId }?.source?.let { SourceNode.fromKey(it).getName() } ?: "Choose Source") {
                                    iconLeft = Icons.MENU_ADD
                                    iconSize = IconSize.SMALL
                                    buttonBlock = {
                                        hxGet("/app/worlds/$worldId/projects/$projectId/resources/gathering/$gatheringId/select-path/expand?nodeItemId=${requiredTree.targetItem.itemId}&depth=$depth&maxBranches=$maxBranches&path=${currentPathEncoded}")
                                        hxTarget("closest .path-requirement")
                                        hxSwap("innerHTML")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Breadcrumb navigation showing current selection path
 */
fun DIV.pathBreadcrumb(tree: ProductionTree, selectedPath: ProductionPath?) {
    nav("path-breadcrumb") {
        attributes["aria-label"] = "Selection path"

        if (selectedPath != null) {
            val items = buildBreadcrumbItems(selectedPath)
            ol("path-breadcrumb__list") {
                items.forEachIndexed { index, item ->
                    li("path-breadcrumb__item") {
                        if (index < items.size - 1) {
                            +"${item.substringAfter("minecraft:")} → "
                        } else {
                            span("path-breadcrumb__current") {
                                +item.substringAfter("minecraft:")
                            }
                        }
                    }
                }
            }
        } else {
            p("subtle") {
                +"Select a source to begin"
            }
        }
    }
}

/**
 * Summary panel showing selection progress
 */
fun DIV.pathSummary(worldId: Int, projectId: Int, gatheringId: Int, selectedPath: ProductionPath?) {
    div("path-summary") {
        h4 {
            +"Selection Summary"
        }

        if (selectedPath != null) {
            val uniqueItems = selectedPath.getAllItemIds()
            val decisions = selectedPath.countDecisions()
            val isComplete = selectedPath.isComplete()

            dl("path-summary__stats") {
                dt { +"Unique items:" }
                dd { +"${uniqueItems.size}" }

                dt { +"Decision points:" }
                dd { +"$decisions" }

                dt { +"Status:" }
                dd {
                    if (isComplete) {
                        span("badge badge--success") {
                            +"Complete"
                        }
                    } else {
                        span("badge badge--warning") {
                            +"Incomplete"
                        }
                    }
                }
            }

            div("path-summary__actions") {
                button(classes = "btn btn--primary") {
                    type = ButtonType.button
                    disabled = !isComplete

                    if (isComplete) {
                        onClick = "alert('Path complete!\\n\\nDatabase persistence will be implemented in Phase 2.1.\\nCurrently the path is stored in the URL and maintained during your session.');"
                        +"Confirm Path (Preview)"
                    } else {
                        +"Complete path to confirm"
                    }
                }
            }
        } else {
            p("subtle") {
                +"No path selected yet"
            }
        }
    }
}

// Helper functions

private fun buildBreadcrumbItems(path: ProductionPath): List<String> {
    val items = mutableListOf(path.itemId)
    path.requirements.forEach { req ->
        items.addAll(buildBreadcrumbItems(req))
    }
    return items
}

private fun getSourceIcon(sourceTypeId: String): String {
    return when {
        sourceTypeId.contains("crafting") -> "⚒️"
        sourceTypeId.contains("smelting") -> "🔥"
        sourceTypeId.contains("mining") -> "⛏️"
        sourceTypeId.contains("loot") -> "📦"
        sourceTypeId.contains("mob") -> "⚔️"
        sourceTypeId.contains("shearing") -> "✂️"
        sourceTypeId.contains("trading") -> "💰"
        else -> "•"
    }
}

/**
 * Check if a specific source is selected for an item anywhere in the path tree
 */
private fun isSourceSelectedForItem(path: ProductionPath?, itemId: String, sourceKey: String): Boolean {
    if (path == null) return false

    // Check if this path node matches
    if (path.itemId == itemId && path.source == sourceKey) {
        return true
    }

    // Recursively check requirements
    return path.requirements.any { isSourceSelectedForItem(it, itemId, sourceKey) }
}
