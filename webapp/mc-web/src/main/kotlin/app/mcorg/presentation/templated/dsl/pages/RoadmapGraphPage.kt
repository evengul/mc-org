package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapNode
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.Graph
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.GraphNode
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.NodeKind
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.Tone
import app.mcorg.presentation.templated.dsl.BadgeStatus
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.templated.dsl.progressBar
import app.mcorg.presentation.templated.dsl.statusBadge
import app.mcorg.presentation.templated.dsl.WorldTab
import app.mcorg.presentation.templated.dsl.worldBar
import app.mcorg.presentation.templated.dsl.pages.newProjectAffordance
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.span

/**
 * Everything the graph view renders, assembled by the handler so the template stays pure.
 *
 * [graph] is null where the world has no chain to draw — a world whose projects share no
 * edges. The page then keeps its list sections and says so, rather than drawing an empty panel.
 */
data class RoadmapGraphView(
    val roadmap: Roadmap,
    val graph: Graph?,
    val startHere: RoadmapNode?,
    val startHereNote: String?,
    val producerCount: Int,
    val producerRows: List<ProducerRow>,
    val unchained: List<UnchainedRow>,
    val terminal: RoadmapNode?,
    val terminalStats: RoadmapGraphLayout.TerminalStats,
    val manualEdgeNote: String?,
    val dataGaps: List<DataGap>,
) {
    data class ProducerRow(val projectId: Int, val name: String, val items: Long, val edges: Int)

    data class UnchainedRow(val projectId: Int, val name: String, val note: String, val tone: Tone)

    /** A finished farm that declares no productions — it supplies nothing and nobody notices. */
    data class DataGap(val projectId: Int, val message: String)
}

/**
 * The world roadmap as a weighted dependency graph (MCO-469).
 *
 * Replaces the flat table as the default view — the table survives behind the view switch,
 * unchanged, because it is still the form that survives hundreds of rows and a screen reader.
 *
 * The design's whole claim is that **sequence and supply are different questions**: the top
 * band is what is left to do, in order; the left column is what already feeds the plan and is
 * waiting on nothing. Nothing done appears in the band and nothing unbuilt appears in the
 * column. See [RoadmapGraphLayout] for the geometry that encodes it.
 */
fun roadmapGraphPage(
    user: TokenProfile,
    view: RoadmapGraphView,
    isWorldAdmin: Boolean = false,
): String = pageShell(
    pageTitle = "Seam — ${view.roadmap.worldName} roadmap",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/components/badge.css",
        "/static/styles/components/progress.css",
        "/static/styles/components/callout.css",
        "/static/styles/pages/roadmap.css",
        "/static/styles/pages/roadmap-graph.css",
        "/static/styles/components/world-tabs.css",
        "/static/styles/components/np-menu.css",
        "/static/styles/components/empty-state.css",
        "/static/styles/components/modal.css",
        "/static/styles/components/form.css",
        "/static/styles/components/item-search.css",
        "/static/styles/components/item-glyph.css",
    ),
    scripts = listOf("/static/scripts/np-menu.js", "/static/scripts/farm-modal.js"),
) {
    appHeader(
        worldName = view.roadmap.worldName,
        worldId = view.roadmap.worldId,
        user = user,
        isWorldAdmin = isWorldAdmin,
        // The breadcrumb locates the *world*; which section of it you are in is the tab
        // bar's job (MCO-474).
        breadcrumbBlock = {
            link("Worlds", "/worlds").current(view.roadmap.worldName)
        }
    )
    main {
        container {
            worldBar(view.roadmap.worldId, WorldTab.ROADMAP) {
                newProjectAffordance(view.roadmap.worldId, showMenu = !view.roadmap.isEmpty())
            }
            // The title sits outside the card, as on the table view and every other page
            // (MCO-505). It used to be an `.rmg-section` within it. It heads an empty world
            // too — the Projects tab heads itself in both states, and a Roadmap tab that
            // dropped its heading when empty made the two tabs look like different pages.
            roadmapTitle(view.roadmap.worldId, headerMeta(view), graphActive = true)
            if (view.roadmap.isEmpty()) {
                // This is the view a world opens on, so it is the first thing a new world
                // shows — and it used to be an empty card with four empty sections in it.
                // The world's one empty state answers it instead (see [worldEmptyState]).
                worldEmptyState(view.roadmap.worldId)
            } else {
                div("rmg-card") {
                    id = "roadmap-graph"
                    startHereSection(view)
                    graphSection(view)
                    unchainedSection(view)
                    producingSection(view)
                }
            }
        }
    }
}

private fun headerMeta(view: RoadmapGraphView): String {
    val stats = view.roadmap.getStatistics()
    return buildList {
        add(view.roadmap.worldName)
        add("${stats.totalProjects} ${if (stats.totalProjects == 1) "project" else "projects"}")
        if (view.producerCount > 0) {
            add("${view.producerCount} producing ${if (view.producerCount == 1) "farm" else "farms"}")
        }
        // Guarded on dependencies, exactly as the table's `roadmapSummary` guards it: depth is
        // a fact about links, and a world whose projects link to nothing is one layer only in
        // the sense that everything is in it. Unguarded this said "0 layers" on an empty world
        // and "1 layer" on an unlinked one, neither of which measures anything.
        if (stats.totalDependencies > 0) {
            add("${stats.maxDepth} ${if (stats.maxDepth == 1) "layer" else "layers"}")
        }
    }.joinToString(" · ")
}

// ---- 2. start here + graph shape --------------------------------------------------------

private fun FlowContent.startHereSection(view: RoadmapGraphView) {
    val start = view.startHere ?: return
    div("rmg-section rmg-start") {
        div("rmg-start__main") {
            span("rmg-label") { +"START HERE" }
            div("rmg-start__name-row") {
                a(classes = "rmg-start__name") {
                    href = "/worlds/${view.roadmap.worldId}/projects/${start.projectId}"
                    +start.projectName
                }
                statusBadge(badgeFor(start))
            }
            view.startHereNote?.let { note -> p("rmg-start__note") { +note } }
            if (start.tasksTotal > 0) {
                div("rmg-start__tasks") {
                    span("rmg-label rmg-start__tasks-label") { +"TASKS" }
                    progressBar(start.tasksCompleted, start.tasksTotal, large = true)
                    span("rmg-start__tasks-count") {
                        +"${start.tasksCompleted} / ${start.tasksTotal}"
                    }
                }
            }
        }
        div("rmg-start__aside") {
            span("rmg-label") { +"AT A GLANCE" }
            div("rmg-deflist") {
                shapeRows(view).forEach { (key, value) ->
                    span("rmg-deflist__key") { +key }
                    span { +value }
                }
            }
        }
    }
}

/**
 * Facts about the *world*, not about the graph that draws it.
 *
 * This used to report "layer 0 / layers 1–3" — the topological sort's own vocabulary, which
 * describes how the ordering is computed rather than anything the reader owns. Depth is the one
 * genuinely useful thing inside it, so it survives as "longest chain", which answers a question
 * somebody actually has: how many projects stand between me and the far end.
 */
private fun shapeRows(view: RoadmapGraphView): List<Pair<String, String>> {
    val stats = view.roadmap.getStatistics()
    val remaining = stats.totalProjects - stats.completedProjects

    return buildList {
        if (remaining > 0) {
            add("still to build" to "$remaining of ${stats.totalProjects} projects")
        }
        if (stats.maxDepth > 1) {
            add("longest chain" to "${stats.maxDepth} projects deep")
        }
        view.terminal?.let { terminal ->
            // Items, not edge count. The old row said "86 from 22 farms", where 86 was the
            // number of supply relationships — and it read as a quantity of items.
            val items = view.roadmap.edges
                .filter { it.fromNodeId == terminal.projectId }
                .sumOf { it.quantity ?: 0L }
            if (items > 0) {
                val farms = if (view.producerCount == 1) "farm" else "farms"
                // Not "feeding <name>": the name had to be shortened to fit a 320px aside, and
                // the only cheap way to do that was to take the last word — which gives "YAMS"
                // for "Storage System YAMS" but "North" for "Iron Farm North". The graph names
                // the destination a few centimetres away; this row does not need to.
                add(
                    "feeding" to
                        "${RoadmapGraphLayout.format(items)} items from ${view.producerCount} $farms"
                )
            }
        }
    }
}

// ---- 3. the graph -----------------------------------------------------------------------

private fun FlowContent.graphSection(view: RoadmapGraphView) {
    val graph = view.graph
    div("rmg-section rmg-graph") {
        div("rmg-graph__head") {
            span("rmg-label") { +"DEPENDENCY GRAPH · LINE WEIGHT = ITEMS MOVED" }
            span("rmg-legend") {
                span { +"▬ GENERATED" }
                span { +"┄ MANUAL / BY HAND" }
                span { +"▸ TAP A PROJECT TO OPEN IT" }
            }
        }

        if (graph == null) {
            div("rmg-graph__empty") {
                +"No project in this world supplies another yet, so there is no chain to draw. "
                +"Define some resources and the graph fills in."
            }
            return@div
        }

        div("rmg-panel") {
            attributes["style"] = "width: ${graph.width}px; height: ${graph.height}px"
            edgeSvg(graph)
            // Document order is sequence → terminal → each group with its own nodes. Desktop
            // ignores it (everything is absolutely positioned); the mobile fallback drops the
            // positioning and reads exactly this order, which is the linear queue the design
            // asks for below 768px.
            graph.nodes
                .filter { it.kind == NodeKind.START || it.kind == NodeKind.SEQUENCE }
                .forEach { node -> graphNode(view, node) }
            graph.nodes
                .filter { it.kind == NodeKind.TERMINAL }
                .forEach { node -> graphNode(view, node) }
            graph.groups.forEach { group ->
                groupHeader(group)
                graph.nodes
                    .filter { it.kind in group.kinds }
                    .forEach { node -> graphNode(view, node) }
            }
            graph.bandCaption?.let { caption ->
                div("rmg-band-caption") {
                    attributes["style"] = "left: ${caption.x}px; top: ${caption.y}px"
                    +caption.text
                }
            }
        }

        view.manualEdgeNote?.let { note ->
            div("callout callout--info") {
                span("callout__icon") { +"i" }
                div("callout__body") { +note }
            }
        }
    }
}

/**
 * Edges as one inline SVG, painted *under* the HTML nodes.
 *
 * Emitted as raw markup because kotlinx.html has no SVG builders — the same route
 * [app.mcorg.presentation.templated.dsl.lucide] takes. Every interpolated value is either a
 * number this file computed or run through [escape]; nothing user-authored reaches the markup
 * unescaped.
 */
private fun FlowContent.edgeSvg(graph: Graph) {
    val paths = graph.edges.joinToString("\n") { edge ->
        val dash = if (edge.dashed) """ stroke-dasharray="5 4"""" else ""
        val label = edge.label?.let {
            """<text x="${it.x}" y="${it.y}" class="rmg-edge__label" text-anchor="${it.anchor}">${escape(it.text)}</text>"""
        } ?: ""
        """<path d="${escape(edge.path)}" class="rmg-edge" stroke-width="${edge.strokeWidth}"$dash marker-end="url(#rmg-arrow)"></path>$label"""
    }

    consumer.onTagContentUnsafe {
        +"""
        <svg class="rmg-panel__edges" viewBox="0 0 ${graph.width} ${graph.height}" width="${graph.width}" height="${graph.height}" aria-hidden="true">
          <defs>
            <marker id="rmg-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" class="rmg-edge__head"></path>
            </marker>
          </defs>
          $paths
        </svg>
        """.trimIndent()
    }
}

private fun FlowContent.groupHeader(group: RoadmapGraphLayout.GroupHeader) {
    div("rmg-group ${toneClass(group.tone)}") {
        attributes["style"] = "top: ${group.y}px"
        span("rmg-group__text") { +group.text }
        group.note?.let { span("rmg-group__note") { +it } }
    }
    div("rmg-group__rule${if (group.dashedRule) " rmg-group__rule--dashed" else ""}") {
        attributes["style"] = "top: ${group.ruleY}px"
    }
}

/**
 * One node. Position is the only thing that reaches the `style` attribute — it is computed
 * per world and cannot live in a stylesheet — following [progressBar], which does the same
 * for its width. Everything else is a class.
 */
private fun FlowContent.graphNode(view: RoadmapGraphView, node: GraphNode) {
    val geometry = buildString {
        append("left: ${node.x}px; top: ${node.y}px; width: ${node.width}px")
        if (node.height > 0) append("; height: ${node.height}px")
    }

    val body: FlowContent.() -> Unit = {
        node.eyebrow?.let { span("rmg-node__eyebrow ${toneClass(node.eyebrowTone)}") { +it } }
        div("rmg-node__title") { +node.title }
        node.subLines.forEach { line ->
            div("rmg-node__sub ${toneClass(line.tone)}") { +line.text }
        }
        if (node.kind == NodeKind.TERMINAL) terminalBody(view)
    }

    if (node.projectId != null) {
        a(classes = "rmg-node ${kindClass(node.kind)}") {
            href = "/worlds/${view.roadmap.worldId}/projects/${node.projectId}"
            attributes["style"] = geometry
            body()
        }
    } else {
        div("rmg-node ${kindClass(node.kind)}") {
            attributes["style"] = geometry
            body()
        }
    }
}

private fun FlowContent.terminalBody(view: RoadmapGraphView) {
    val stats = view.terminalStats
    div("rmg-terminal__progress") {
        progressBar(stats.percentComplete, 100, large = true)
        span("rmg-terminal__percent") { +"${stats.percentComplete}%" }
    }
    div("rmg-deflist rmg-terminal__facts") {
        span("rmg-deflist__key") { +"from farms" }
        span { +RoadmapGraphLayout.format(stats.fromFarms) }
        span("rmg-deflist__key") { +"by hand" }
        span { +RoadmapGraphLayout.format(stats.byHand) }
        span("rmg-deflist__key") { +"craft steps" }
        span { +"${RoadmapGraphLayout.format(stats.craftRows.toLong())} rows" }
        span("rmg-deflist__key") { +"open questions" }
        span(if (stats.openQuestions > 0) "rmg-tone-amber" else null) { +"${stats.openQuestions}" }
    }
}

// ---- 4. not in any chain -----------------------------------------------------------------

private fun FlowContent.unchainedSection(view: RoadmapGraphView) {
    if (view.unchained.isEmpty()) return
    div("rmg-section rmg-unchained") {
        div("rmg-unchained__head") {
            span("rmg-label") { +"NOT IN ANY CHAIN · ${view.unchained.size}" }
            span("rmg-note") { +"nothing supplies them, they supply nothing — do them whenever" }
        }
        div("rmg-chips") {
            view.unchained.forEach { row ->
                a(classes = "rmg-chip") {
                    href = "/worlds/${view.roadmap.worldId}/projects/${row.projectId}"
                    span("rmg-chip__name") { +row.name }
                    span("rmg-chip__note ${toneClass(row.tone)}") { +row.note }
                }
            }
        }
    }
}

// ---- 5. producing ------------------------------------------------------------------------

private fun FlowContent.producingSection(view: RoadmapGraphView) {
    if (view.producerRows.isEmpty()) return
    div("rmg-section rmg-producing") {
        div("rmg-producing__head") {
            span("rmg-label") {
                +"PRODUCING · ${view.producerCount} ${if (view.producerCount == 1) "FARM" else "FARMS"}"
            }
            span("rmg-note") { +"done, and still feeding the roadmap · items · supply lines" }
        }
        div("rmg-grid") {
            view.producerRows.forEachIndexed { index, row ->
                // Rules live on the cells, never as a gap over a tinted container: with a cell
                // count that is not a multiple of three, the trailing empty grid areas would
                // expose the container colour as a solid slab.
                val band = if ((index / 3) % 2 == 0) " rmg-grid__cell--band" else ""
                a(classes = "rmg-grid__cell$band") {
                    href = "/worlds/${view.roadmap.worldId}/projects/${row.projectId}"
                    span { +row.name }
                    span("rmg-grid__meta") {
                        +"${RoadmapGraphLayout.format(row.items)} · ${row.edges}"
                    }
                }
            }
        }
        view.dataGaps.forEach { gap ->
            div("rmg-datagap") {
                span("rmg-datagap__text") { +gap.message }
                a(classes = "rmg-datagap__fix") {
                    href = "/worlds/${view.roadmap.worldId}/projects/${gap.projectId}"
                    +"fix ▸"
                }
            }
        }
    }
}

// ---- shared -------------------------------------------------------------------------------

private fun badgeFor(node: RoadmapNode): BadgeStatus = when {
    node.isBlocked -> BadgeStatus.BLOCKED
    node.tasksCompleted > 0 -> BadgeStatus.IN_PROGRESS
    else -> BadgeStatus.NOT_STARTED
}

private fun kindClass(kind: NodeKind): String = when (kind) {
    NodeKind.SEQUENCE -> "rmg-node--sequence"
    NodeKind.START -> "rmg-node--start"
    NodeKind.SUPPLY -> "rmg-node--supply"
    NodeKind.BUNDLE -> "rmg-node--bundle"
    NodeKind.HAND -> "rmg-node--hand"
    NodeKind.TERMINAL -> "rmg-node--terminal"
}

private fun toneClass(tone: Tone): String = when (tone) {
    Tone.DEFAULT -> ""
    Tone.MUTED -> "rmg-tone-muted"
    Tone.GREEN -> "rmg-tone-green"
    Tone.RED -> "rmg-tone-red"
    Tone.AMBER -> "rmg-tone-amber"
    Tone.ACCENT -> "rmg-tone-accent"
    Tone.DISABLED -> "rmg-tone-disabled"
}

private fun escape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
