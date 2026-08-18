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
    // Both directions read the *same* edge set (MCO-318). Filtering one side to blocking edges
    // and not the other made the table contradict itself: a DONE farm claimed to block a project
    // whose own row said nothing blocked it. Every edge shows on both rows; whether it is still
    // blocking or already supplying is said in the cell, not by omitting it from one column.
    val upstreamByConsumer = roadmap.edges.groupBy { it.fromNodeId }
    val downstreamByProducer = roadmap.edges.groupBy { it.toNodeId }

    // Unfinished work first, then depth (MCO-405).
    //
    // Depth alone was the sequence, and it is the right sequence — but ascending depth answers
    // "what came first", and the question at the top of a roadmap is "what do I do next". On the
    // dev world that put 20-odd DONE farms above the one active build, alphabetically, because a
    // finished prerequisite genuinely has depth 0.
    //
    // Terminal rather than DONE: cancelled and archived projects are not what to do next either,
    // and they are not blocking anything downstream. Within each band the old rule is untouched —
    // depth, then blocked sinks, then name for a stable order between renders — so the layer
    // column still reads as the sequence it always did, and the Layer cell still prints it.
    val rows = roadmap.nodes.sortedWith(
        compareBy<RoadmapNode> { it.state.isTerminal }
            .thenBy { it.layer }
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
                    th { +"Depends on" }
                    th { +"Supplies" }
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
                        td {
                            attributes["data-label"] = "Depends on"
                            dependsOnCell(roadmap.worldId, upstreamByConsumer[node.projectId].orEmpty())
                        }
                        td {
                            attributes["data-label"] = "Supplies"
                            suppliesCell(roadmap.worldId, downstreamByProducer[node.projectId].orEmpty())
                        }
                    }
                }
            }
        }
    }
}

/**
 * One line of an edge cell, in whichever direction it is read: the project at the other end,
 * the resource when the cell names it, and what the relationship currently *is*.
 *
 * [itemName] is what the cell prints and is null wherever the cell doesn't name the resource.
 * [isResourceEdge] is about the edge itself — false only for a manual project→project
 * sequencing edge, which is about no resource at all. MCO-316 hangs demand quantities off this
 * type, so keep it the single shape both directions are mapped into.
 */
private data class RoadmapEdgeEntry(
    val projectId: Int,
    val projectName: String,
    val itemName: String?,
    val isResourceEdge: Boolean,
    val isBlocking: Boolean,
    /** Derived plan demand (MCO-316); null when the edge carries no number. */
    val quantity: Long? = null,
)

/**
 * Upstream: what this project is waiting on, or already being fed by. Names the project *and*
 * the resource, per the IA: "Iron Farm — Iron Ingot". Knowing which project you depend on is
 * only half an answer; the other half is what you are getting from it. A manual sequencing edge
 * has no resource, so it names the project alone.
 */
private fun FlowContent.dependsOnCell(worldId: Int, edges: List<RoadmapEdge>) {
    roadmapEdgeCell(
        worldId,
        edges.map { edge ->
            RoadmapEdgeEntry(
                projectId = edge.toNodeId,
                projectName = edge.toNodeName,
                itemName = edge.itemName,
                isResourceEdge = edge.itemName != null,
                isBlocking = edge.isBlocking,
                quantity = edge.quantity,
            )
        }
    )
}

/**
 * Downstream: who this project feeds. The same edges the consumers' own rows show, so the two
 * columns cannot disagree — a DONE farm supplies its consumers here rather than claiming to
 * block them. Names only; the resource detail lives on the consumer's row.
 */
private fun FlowContent.suppliesCell(worldId: Int, edges: List<RoadmapEdge>) {
    roadmapEdgeCell(
        worldId,
        edges
            .groupBy { it.fromNodeId }
            .map { (consumerId, consumerEdges) ->
                RoadmapEdgeEntry(
                    projectId = consumerId,
                    projectName = consumerEdges.first().fromNodeName,
                    itemName = null,
                    isResourceEdge = consumerEdges.any { it.itemName != null },
                    // Several resources can run along the same pair of projects; the pair is
                    // blocking if any one of them still is.
                    isBlocking = consumerEdges.any { it.isBlocking },
                )
            }
    )
}

/**
 * Blocking is called out in words, not just colour — the primary user is red-green colour-blind,
 * and "this is still holding you up" is the one thing in the cell that must not be missed.
 */
private fun FlowContent.roadmapEdgeCell(worldId: Int, entries: List<RoadmapEdgeEntry>) {
    if (entries.isEmpty()) {
        span("roadmap-muted") { +"—" }
        return
    }
    div("roadmap-edge-list") {
        entries
            // Blockers first — they are the actionable ones — then a stable alphabetical order.
            .sortedWith(
                compareByDescending<RoadmapEdgeEntry> { it.isBlocking }
                    .thenBy { it.projectName }
                    .thenBy { it.itemName ?: "" }
            )
            .forEach { entry ->
                val modifier = if (entry.isBlocking) "roadmap-edge--blocking" else "roadmap-edge--supplying"
                div("roadmap-edge $modifier") {
                    a(classes = "roadmap-project-link") {
                        href = "/worlds/$worldId/projects/${entry.projectId}"
                        +entry.projectName
                    }
                    entry.itemName?.let { item ->
                        // MCO-316 — first pass, deliberately the plainest thing that stops the
                        // cell misleading. "Cobblestone Generator — 74,564 Cobblestone" instead
                        // of a bare item name that used to sit next to a single decorative block.
                        val amount = entry.quantity?.let { "${"%,d".format(it)} " } ?: ""
                        span("roadmap-edge__item") { +" — $amount$item" }
                    }
                    span("roadmap-edge__status") { +entry.statusLabel() }
                }
            }
    }
}

/**
 * "blocking" for a producer that is not DONE yet; otherwise the relationship is live —
 * "supplying" when there is a resource flowing, and a plain "done" for a manual sequencing
 * edge, where nothing is being supplied and the only news is that the prerequisite is met.
 */
private fun RoadmapEdgeEntry.statusLabel(): String = when {
    isBlocking -> "blocking"
    isResourceEdge -> "supplying"
    else -> "done"
}
