package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapEdge
import app.mcorg.domain.model.world.RoadmapNode
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.emptyState
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.templated.dsl.projectStateBadge
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.main
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

/**
 * The world roadmap (MCO-288) — every project as a row, ordered by dependency depth, so the
 * sequence the world implies is readable top to bottom.
 *
 * Table form on purpose: the layered graph the model already computes is the follow-up, and
 * a table is the accessible, mobile-survivable, hundreds-of-rows form to lead with. Nothing
 * here is editable — the roadmap is derived from resource relationships, never curated.
 */
fun roadmapPage(
    user: TokenProfile,
    roadmap: Roadmap,
    isWorldAdmin: Boolean = false,
): String = pageShell(
    pageTitle = "Seam — ${roadmap.worldName} roadmap",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/components/badge.css",
        "/static/styles/pages/roadmap.css",
    ),
) {
    appHeader(
        worldName = roadmap.worldName,
        worldId = roadmap.worldId,
        user = user,
        isWorldAdmin = isWorldAdmin,
        breadcrumbBlock = {
            link("Worlds", "/worlds")
                .link(roadmap.worldName, "/worlds/${roadmap.worldId}/projects")
                .current("Roadmap")
        }
    )
    main {
        container {
            div("roadmap-title") {
                h1("roadmap-title__name") { +"Roadmap" }
                div("roadmap-title__meta") { +roadmapSummary(roadmap) }
            }

            if (roadmap.isEmpty()) {
                roadmapEmptyState(roadmap.worldId)
            } else {
                roadmapTable(roadmap)
            }
        }
    }
}

/** "12 projects · 3 blocked · 4 layers deep" — straight off the model's own statistics. */
private fun roadmapSummary(roadmap: Roadmap): String {
    val stats = roadmap.getStatistics()
    val parts = buildList {
        add("${stats.totalProjects} ${if (stats.totalProjects == 1) "project" else "projects"}")
        if (stats.blockedProjects > 0) add("${stats.blockedProjects} blocked")
        if (stats.totalDependencies > 0) {
            add("${stats.maxDepth} ${if (stats.maxDepth == 1) "layer" else "layers"} deep")
        }
    }
    return parts.joinToString(" · ")
}

/**
 * Shown when the world has no projects at all. A full page with a way forward, not a hidden
 * feature — someone who followed the roadmap link wants to know what would fill it.
 */
private fun FlowContent.roadmapEmptyState(worldId: Int) {
    emptyState(
        heading = "Nothing to sequence yet",
        body = "The roadmap draws itself from your projects' resources: when one project's " +
            "requirement is produced or solved by another, an edge appears here and the order " +
            "follows. Define some resources to see it fill in.",
    ) {
        a(classes = "btn btn--primary") {
            href = "/worlds/$worldId/projects"
            +"Back to projects"
        }
    }
}

private fun FlowContent.roadmapTable(roadmap: Roadmap) {
    val blockingByConsumer = roadmap.edges.filter { it.isBlocking }.groupBy { it.fromNodeId }
    val consumersByProducer = roadmap.edges.groupBy { it.toNodeId }

    // Depth first — that is the sequence. Blocked projects sink within their layer, and
    // names break the remaining ties so the order is stable between renders.
    val rows = roadmap.nodes.sortedWith(
        compareBy<RoadmapNode> { it.layer }
            .thenBy { it.isBlocked }
            .thenBy { it.projectName }
    )

    div("roadmap-table-wrap") {
        table(classes = "data-table roadmap-table") {
            id = "roadmap-table"
            thead {
                tr {
                    th { +"Layer" }
                    th { +"Project" }
                    th { +"State" }
                    th { +"Tasks" }
                    th { +"Blocked by" }
                    th { +"Blocks" }
                }
            }
            tbody {
                rows.forEach { node ->
                    tr {
                        td {
                            attributes["data-label"] = "Layer"
                            span("roadmap-layer") { +node.layer.toString() }
                        }
                        td {
                            attributes["data-label"] = "Project"
                            a(classes = "roadmap-project-link") {
                                href = "/worlds/${roadmap.worldId}/projects/${node.projectId}"
                                +node.projectName
                            }
                        }
                        td {
                            attributes["data-label"] = "State"
                            projectStateBadge(node.projectId, node.state)
                        }
                        td {
                            attributes["data-label"] = "Tasks"
                            if (node.tasksTotal == 0) {
                                span("roadmap-muted") { +"—" }
                            } else {
                                +"${node.tasksCompleted} / ${node.tasksTotal}"
                            }
                        }
                        td("roadmap-cell--blocked") {
                            attributes["data-label"] = "Blocked by"
                            blockedByCell(roadmap.worldId, blockingByConsumer[node.projectId].orEmpty())
                        }
                        td {
                            attributes["data-label"] = "Blocks"
                            blocksCell(roadmap.worldId, consumersByProducer[node.projectId].orEmpty())
                        }
                    }
                }
            }
        }
    }
}

/**
 * Names the project *and* the resource, per the IA: "Iron Farm — Iron Ingot". Knowing which
 * project blocks you is only half an answer; the other half is what you are waiting for.
 * A manual sequencing edge has no resource, so it names the project alone.
 */
private fun FlowContent.blockedByCell(worldId: Int, edges: List<RoadmapEdge>) {
    if (edges.isEmpty()) {
        span("roadmap-muted") { +"—" }
        return
    }
    div("roadmap-edge-list") {
        edges
            .sortedWith(compareBy({ it.toNodeName }, { it.itemName ?: "" }))
            .forEach { edge ->
                div("roadmap-edge") {
                    a(classes = "roadmap-project-link") {
                        href = "/worlds/$worldId/projects/${edge.toNodeId}"
                        +edge.toNodeName
                    }
                    edge.itemName?.let { item ->
                        span("roadmap-edge__item") { +" — $item" }
                    }
                }
            }
    }
}

/** The other direction: who is waiting on this project. Names only — the detail is on their row. */
private fun FlowContent.blocksCell(worldId: Int, edges: List<RoadmapEdge>) {
    val consumers = edges
        .distinctBy { it.fromNodeId }
        .sortedBy { it.fromNodeName }
    if (consumers.isEmpty()) {
        span("roadmap-muted") { +"—" }
        return
    }
    div("roadmap-edge-list") {
        consumers.forEach { edge ->
            div("roadmap-edge") {
                a(classes = "roadmap-project-link") {
                    href = "/worlds/$worldId/projects/${edge.fromNodeId}"
                    +edge.fromNodeName
                }
            }
        }
    }
}
