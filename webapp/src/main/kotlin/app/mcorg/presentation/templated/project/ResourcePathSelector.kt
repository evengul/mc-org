package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.resources.ProductionPath
import app.mcorg.domain.model.resources.SourceNode
import app.mcorg.domain.services.ProductionBranch
import app.mcorg.domain.services.ProductionTree
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxPut
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.ghostButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.ChipSize
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import kotlinx.html.*

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
    maxBranches: Int,
    confirmed: Boolean = false
) {
    div("path-selector") {
        id = "path-selector-$gatheringId"

        // Breadcrumb navigation
        pathBreadcrumb(tree, selectedPath)

        // Main tree view
        div("path-selector__tree") {
            h3 {
                +"Select how to obtain ${tree.targetItem.item.name}"
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
                    val remaining = tree.sources.size - maxBranches
                    div("path-selector__more-options") {
                        ghostButton("Show $remaining more option${if (remaining > 1) "s" else ""}") {
                            iconLeft = Icons.MENU_ADD
                            iconSize = IconSize.SMALL
                            buttonBlock = {
                                hxGet("/app/worlds/$worldId/projects/$projectId/resources/gathering/$gatheringId/select-path?depth=$depth&maxBranches=${maxBranches + 3}")
                                hxTarget("#path-selector-$gatheringId")
                                hxSwap("outerHTML")
                            }
                        }
                    }
                }
            }
        }

        // Selection summary panel
        pathSummary(worldId, projectId, gatheringId, selectedPath, confirmed)
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
            hxPut("/app/worlds/$worldId/projects/$projectId/resources/gathering/$gatheringId/select-path")
            attributes["hx-vals"] = """{"itemId": "$itemId", "sourceType": "${branch.source.getKey()}"}"""
            hxTarget("#path-selector-$gatheringId")
            hxSwap("outerHTML")

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
                span("path-node__count") {
                    chipComponent {
                        variant = ChipVariant.NEUTRAL
                        size = ChipSize.SMALL
                        +"${branch.requiredItems.size} ${if (branch.requiredItems.size == 1) "requirement" else "requirements"}"
                    }
                }
            }

            // Selection indicator
            if (isSelected) {
                span("path-node__check") {
                    +"\u2713"
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
                                + requiredTree.targetItem.item.name
                            }

                            if (requiredTree.sources.isEmpty()) {
                                chipComponent {
                                    variant = ChipVariant.SUCCESS
                                    size = ChipSize.SMALL
                                    +"Base resource"
                                }
                            } else {
                                val existingSource = selectedPath?.let { findSourceTypeName(it, requiredTree.targetItem.itemId) }
                                if (existingSource != null) {
                                    ghostButton("$existingSource (Change)") {
                                        iconLeft = Icons.MENU_ADD
                                        iconSize = IconSize.SMALL
                                        buttonBlock = {
                                            hxGet("/app/worlds/$worldId/projects/$projectId/resources/gathering/$gatheringId/select-path/expand?nodeItemId=${requiredTree.targetItem.itemId}&depth=$depth&maxBranches=$maxBranches")
                                            hxTarget("closest .path-requirement")
                                            hxSwap("innerHTML")
                                        }
                                    }
                                } else {
                                    neutralButton("Choose Source") {
                                        iconLeft = Icons.MENU_ADD
                                        iconSize = IconSize.SMALL
                                        buttonBlock = {
                                            hxGet("/app/worlds/$worldId/projects/$projectId/resources/gathering/$gatheringId/select-path/expand?nodeItemId=${requiredTree.targetItem.itemId}&depth=$depth&maxBranches=$maxBranches")
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
}

private fun findSourceTypeName(path: ProductionPath, targetItemId: String): String? {
    if (path.item.id == targetItemId) {
        return path.source?.let { SourceNode.fromKey(it.sourceKey).getName() }
    }
    for (req in path.source?.requirements ?: emptyList()) {
        if (req.item.id == targetItemId) {
            return req.source?.let { SourceNode.fromKey(it.sourceKey).getName() }
        }
    }
    var sourceTypeName: String? = null
    for (req in path.source?.requirements ?: emptyList()) {
        sourceTypeName = findSourceTypeName(req, targetItemId)
    }
    return sourceTypeName
}

/**
 * Breadcrumb navigation showing current selection path
 */
fun DIV.pathBreadcrumb(tree: ProductionTree, selectedPath: ProductionPath?) {
    nav("path-breadcrumb") {
        attributes["aria-label"] = "Selection path"

        if (selectedPath != null) {
            val entries = buildBreadcrumbEntries(selectedPath)
            ol("path-breadcrumb__list") {
                entries.forEachIndexed { index, (itemName, sourceName) ->
                    li("path-breadcrumb__item") {
                        val label = itemName +
                            (sourceName?.let { " ($it)" } ?: "")

                        if (index < entries.size - 1) {
                            +"$label \u2192 "
                        } else {
                            span("path-breadcrumb__current") {
                                +label
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
fun DIV.pathSummary(worldId: Int, projectId: Int, gatheringId: Int, selectedPath: ProductionPath?, confirmed: Boolean = false) {
    div("path-summary") {
        id = "path-summary-$gatheringId"

        h4 {
            +"Selection Summary"
        }

        if (selectedPath != null) {
            val uniqueItems = selectedPath.getAllItemIds()
            val decisions = selectedPath.countDecisions()
            val isComplete = selectedPath.isComplete()

            dl("path-summary__stats") {
                dt { +"Items involved:" }
                dd { +"${uniqueItems.size}" }

                dt { +"Sources selected:" }
                dd { +"$decisions" }

                dt { +"Status:" }
                dd {
                    if (confirmed) {
                        chipComponent {
                            variant = ChipVariant.SUCCESS
                            size = ChipSize.SMALL
                            +"Confirmed"
                        }
                    } else if (isComplete) {
                        chipComponent {
                            variant = ChipVariant.SUCCESS
                            size = ChipSize.SMALL
                            +"Ready to confirm"
                        }
                    } else {
                        chipComponent {
                            variant = ChipVariant.WARNING
                            size = ChipSize.SMALL
                            +"Incomplete"
                        }
                    }
                }
            }

            div("path-summary__actions") {
                if (confirmed) {
                    chipComponent {
                        variant = ChipVariant.SUCCESS
                        +"Path confirmed"
                    }
                    ghostButton("Reconfigure") {
                        buttonBlock = {
                            hxDeleteWithConfirm(
                                url = "/app/worlds/$worldId/projects/$projectId/resources/gathering/$gatheringId/select-path",
                                title = "Reset Resource Path",
                                description = "This will clear your current path selection so you can start over."
                            )
                            hxTarget("#path-selector-$gatheringId")
                            hxSwap("outerHTML")
                        }
                    }
                } else if (isComplete) {
                    actionButton("Confirm Path") {
                        buttonBlock = {
                            hxPut("/app/worlds/$worldId/projects/$projectId/resources/gathering/$gatheringId/select-path/confirm")
                            hxTarget("#path-summary-$gatheringId")
                            hxSwap("outerHTML")
                        }
                    }
                } else {
                    val missingCount = countMissingSources(selectedPath)
                    p("subtle") {
                        +"$missingCount ${if (missingCount == 1) "item still needs" else "items still need"} a source selected"
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

private fun buildBreadcrumbEntries(path: ProductionPath): List<Pair<String, String?>> {
    val sourceName = path.source?.let { SourceNode.fromKey(it.sourceKey).getName() }
    val entries = mutableListOf(path.item.name to sourceName)
    path.source?.requirements?.forEach { req ->
        entries.addAll(buildBreadcrumbEntries(req))
    }
    return entries
}

private fun getSourceIcon(sourceTypeId: String): String {
    return when {
        sourceTypeId.contains("crafting") -> "\u2692\uFE0F"
        sourceTypeId.contains("smelting") -> "\uD83D\uDD25"
        sourceTypeId.contains("mining") -> "\u26CF\uFE0F"
        sourceTypeId.contains("loot") -> "\uD83D\uDCE6"
        sourceTypeId.contains("mob") -> "\u2694\uFE0F"
        sourceTypeId.contains("shearing") -> "\u2702\uFE0F"
        sourceTypeId.contains("trading") -> "\uD83D\uDCB0"
        else -> "\u2022"
    }
}

/**
 * Check if a specific source is selected for an item anywhere in the path tree
 */
private fun isSourceSelectedForItem(path: ProductionPath?, itemId: String, sourceKey: String): Boolean {
    if (path == null) return false

    // Check if this path node matches
    if (path.item.id == itemId && path.source?.sourceKey == sourceKey) {
        return true
    }

    // Recursively check requirements
    return path.source?.requirements?.any { isSourceSelectedForItem(it, itemId, sourceKey) } ?: false
}

/**
 * Count items in the path that still need a source selected
 */
private fun countMissingSources(path: ProductionPath): Int {
    if (path.source == null) return 0
    var missing = 0
    for (req in path.source.requirements) {
        if (req.source == null) {
            missing++
        } else {
            missing += countMissingSources(req)
        }
    }
    return missing
}
