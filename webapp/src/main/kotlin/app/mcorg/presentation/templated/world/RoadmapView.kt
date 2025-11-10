package app.mcorg.presentation.templated.world

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapLayer
import app.mcorg.domain.model.world.RoadmapNode
import app.mcorg.domain.model.world.RoadmapStatistics
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.chip.ChipSize
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.emptystate.EmptyStateVariant
import app.mcorg.presentation.templated.common.emptystate.emptyState
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.progress.progressComponent
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

fun DIV.roadmapView(tabData: WorldPageTabData.RoadmapData) {
    val (_, world, _, roadmap) = tabData

    // Statistics header
    div("roadmap-stats") {
        roadmapStatistics(roadmap.getStatistics(), roadmap)
    }

    // Empty state (if no projects)
    if (roadmap.isEmpty()) {
        roadmapEmptyState(world.id)
        return
    }

    // Layers (grouped by depth)
    div("roadmap-layers") {
        roadmap.layers.sortedBy { it.depth }.forEach { layer ->
            roadmapLayer(layer, roadmap)
        }
    }
}

private fun DIV.roadmapStatistics(stats: RoadmapStatistics, roadmap: Roadmap) {
    div("roadmap-stats-card") {
        div("roadmap-stats-grid") {
            statItem("Total Projects", stats.totalProjects.toString(), "neutral")
            // Calculate ready count (not blocked and in IDEA stage)
            val readyCount = roadmap.nodes.count { it.isReadyToStart() }
            statItem("Can Start Now", readyCount.toString(), "success")
            statItem("In Progress", stats.getInProgressCount().toString(), "action")
            statItem("Blocked", stats.blockedProjects.toString(), "warning")
            statItem("Completed", stats.completedProjects.toString(), "success")
        }

        if (stats.hasBlockedProjects()) {
            div("u-margin-top-md u-flex u-flex-align-center") {
                addComponent(
                    Icons.Notification.WARNING.medium()
                )
                span("u-margin-left-xs") {
                    style = "color: var(--clr-warning); font-weight: bold;"
                    +"${stats.blockedProjects} project${if (stats.blockedProjects == 1) "" else "s"} waiting on dependencies"
                }
            }
        }
        if (stats.completedProjects == stats.totalProjects) {
            div("u-margin-top-md u-width-full u-flex u-flex-center") {
                span("u-margin-left-xs") {
                    style = "color: var(--clr-success); font-weight: bold;"
                    +"All projects completed! 🎉"
                }
            }
        }
    }
}

private fun DIV.statItem(label: String, value: String, variant: String) {
    div("roadmap-stat-item") {
        div("roadmap-stat-value roadmap-stat-value--$variant") {
            +value
        }
        div("roadmap-stat-label") {
            +label
        }
    }
}

private fun DIV.roadmapLayer(layer: RoadmapLayer, roadmap: Roadmap) {
    val layerId = "roadmap-layer-${layer.depth}"

    // Check if all projects in this layer are completed
    val layerProjects = layer.projectIds.mapNotNull { projectId ->
        roadmap.nodes.find { it.projectId == projectId }
    }
    val isLayerCompleted = layerProjects.isNotEmpty() && layerProjects.all { it.isCompleted() }

    div("roadmap-layer${if (isLayerCompleted) " roadmap-layer-collapsed" else ""}") {
        id = layerId

        div("roadmap-layer-header roadmap-layer-header--clickable") {
            attributes["onclick"] = "document.getElementById('$layerId').classList.toggle('roadmap-layer-collapsed')"
            attributes["style"] = "cursor: pointer;"
            attributes["role"] = "button"
            attributes["aria-expanded"] = if (isLayerCompleted) "false" else "true"
            attributes["aria-controls"] = layerId

            div("u-flex u-flex-align-center u-flex-gap-sm") {
                // Collapse indicator
                span("roadmap-layer-toggle") {
                    +"▼"
                }
                h3 {
                    val layerTitle = when {
                        layer.isRootLayer() -> "Layer ${layer.depth + 1} - Start Here"
                        layer.depth == roadmap.getMaxDepth() - 1 -> "Layer ${layer.depth + 1} - Final"
                        else -> "Layer ${layer.depth + 1}"
                    }
                    +layerTitle
                }
            }
            span("roadmap-layer-count") {
                +"${layer.projectCount} project${if (layer.projectCount == 1) "" else "s"}"
            }
        }

        // Projects in this layer (collapsible content)
        div("roadmap-layer-projects") {
            layer.projectIds.forEach { projectId ->
                val node = roadmap.nodes.find { it.projectId == projectId }
                node?.let { roadmapProjectCard(it, roadmap) }
            }
        }
    }
}

private fun DIV.roadmapProjectCard(node: RoadmapNode, roadmap: Roadmap) {
    val cardModifier = getCardModifier(node)

    div("roadmap-project-card${if (cardModifier.isNotEmpty()) " $cardModifier" else ""}") {
        // Status indicator
        div("roadmap-project-status") {
            statusBadge(node)
        }

        // Project header
        div("roadmap-project-header") {
            h4 { +node.projectName }
            chipComponent {
                icon = getIconForType(node.projectType)
                text = node.projectType.toPrettyEnumName()
                variant = ChipVariant.NEUTRAL
                size = ChipSize.SMALL
            }
        }

        // Stage indicator
        div("u-margin-bottom-xs") {
            chipComponent {
                text = node.stage.toPrettyEnumName()
                variant = when (node.stage) {
                    ProjectStage.COMPLETED -> ChipVariant.SUCCESS
                    ProjectStage.IDEA -> ChipVariant.NEUTRAL
                    else -> ChipVariant.ACTION
                }
                size = ChipSize.SMALL
            }
        }

        // Progress
        if (node.tasksTotal > 0) {
            progressComponent {
                value = node.tasksCompleted.toDouble()
                max = node.tasksTotal.toDouble()
                showPercentage = false
                label = "${node.tasksCompleted}/${node.tasksTotal} task${if (node.tasksTotal == 1) "" else "s"}"
            }
        }

        // Dependencies (if any)
        if (node.blockingProjectIds.isNotEmpty()) {
            div("roadmap-dependencies u-margin-top-sm") {
                div("roadmap-dependencies-header") {
                    addComponent(Icons.Notification.WARNING.small())
                    +"Waiting on:"
                }
                ul {
                    node.blockingProjectIds.forEach { depId ->
                        val depNode = roadmap.nodes.find { it.projectId == depId }
                        depNode?.let {
                            li { +it.projectName }
                        }
                    }
                }
            }
        }

        // Unlocks (dependent projects)
        if (node.dependentProjectIds.isNotEmpty()) {
            div("roadmap-unlocks u-margin-top-sm") {
                div("roadmap-unlocks-header") {
                    +"Unlocks:"
                }
                ul("roadmap-unlocks-list") {
                    node.dependentProjectIds.forEach { depId ->
                        val depNode = roadmap.nodes.find { it.projectId == depId }
                        depNode?.let {
                            li { +it.projectName }
                        }
                    }
                }
            }
        }

        // Action button
        div("u-margin-top-md") {
            actionButton("View Details") {
                href = Link.Worlds.world(roadmap.worldId).project(node.projectId).to
                addClass("u-width-full")
            }
        }
    }
}

private fun getCardModifier(node: RoadmapNode): String {
    return when {
        node.isCompleted() -> "roadmap-project-card--completed"
        node.isBlocked -> "roadmap-project-card--blocked"
        node.isReadyToStart() -> "roadmap-project-card--ready"
        node.isInProgress() -> "roadmap-project-card--in-progress"
        else -> ""
    }
}

private fun DIV.statusBadge(node: RoadmapNode) {
    chipComponent {
        when {
            node.isCompleted() -> {
                text = "✓ Completed"
                variant = ChipVariant.SUCCESS
            }
            node.isBlocked -> {
                text = "⚠ Blocked"
                variant = ChipVariant.WARNING
            }
            node.isReadyToStart() -> {
                text = "✓ Ready to Start"
                variant = ChipVariant.SUCCESS
            }
            node.isInProgress() -> {
                text = "⚡ In Progress"
                variant = ChipVariant.ACTION
            }
            else -> {
                text = "○ Not Started"
                variant = ChipVariant.NEUTRAL
            }
        }
        size = ChipSize.SMALL
    }
}

private fun getIconForType(type: app.mcorg.domain.model.project.ProjectType) = when (type) {
    app.mcorg.domain.model.project.ProjectType.BUILDING -> Icons.Menu.PROJECTS
    app.mcorg.domain.model.project.ProjectType.REDSTONE -> Icons.Menu.UTILITIES
    app.mcorg.domain.model.project.ProjectType.MINING -> Icons.Menu.PROJECTS
    app.mcorg.domain.model.project.ProjectType.FARMING -> Icons.Menu.PROJECTS
    app.mcorg.domain.model.project.ProjectType.EXPLORATION -> Icons.Menu.PROJECTS
    app.mcorg.domain.model.project.ProjectType.DECORATION -> Icons.Menu.PROJECTS
    app.mcorg.domain.model.project.ProjectType.TECHNICAL -> Icons.Menu.CONTRAPTIONS
}

private fun DIV.roadmapEmptyState(worldId: Int) {
    emptyState(
        id = "roadmap-empty-state",
        title = "No Projects Yet",
        description = "Create your first project to see the roadmap visualization",
        icon = Icons.Menu.ROAD_MAP,
        variant = EmptyStateVariant.DEFAULT
    ) {
        actionButton("Create Project") {
            href = "/app/worlds/$worldId/projects/new"
        }
    }
}

