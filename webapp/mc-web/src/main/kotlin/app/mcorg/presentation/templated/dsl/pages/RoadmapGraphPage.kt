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
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML

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
    ),
) {
    appHeader(
        worldName = view.roadmap.worldName,
        worldId = view.roadmap.worldId,
        user = user,
        isWorldAdmin = isWorldAdmin,
        breadcrumbBlock = {
            link("Worlds", "/worlds")
                .link(view.roadmap.worldName, "/worlds/${view.roadmap.worldId}/projects")
                .current("Roadmap")
        }
    )
    main {
        container {
            div("rmg-card") {
                id = "roadmap-graph"
                pageHeaderSection(view)
                startHereSection(view)
                graphSection(view)
                unchainedSection(view)
                producingSection(view)
            }
        }
    }
}

/** The graph card alone, for the view switch's HTMX swap. */
fun roadmapGraphFragment(view: RoadmapGraphView): String = createHTML().div("rmg-card") {
    id = "roadmap-graph"
    pageHeaderSection(view)
    startHereSection(view)
    graphSection(view)
    unchainedSection(view)
    producingSection(view)
}

// ---- 1. page header --------------------------------------------------------------------

private fun FlowContent.pageHeaderSection(view: RoadmapGraphView) {
    div("rmg-section rmg-head") {
        div("rmg-head__titles") {
            h1("rmg-head__title") { +"Roadmap" }
            div("rmg-head__meta") { +headerMeta(view) }
        }
        div("rmg-viewswitch") {
            span("rmg-viewswitch__label") { +"VIEW" }
            span("rmg-viewswitch__seg rmg-viewswitch__seg--active") { +"Graph" }
            a(classes = "rmg-viewswitch__seg") {
                href = "/worlds/${view.roadmap.worldId}/roadmap?view=table"
                +"Table"
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
        add("${stats.maxDepth} ${if (stats.maxDepth == 1) "layer" else "layers"}")
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
            span("rmg-label") { +"GRAPH SHAPE" }
            div("rmg-deflist") {
                shapeRows(view).forEach { (key, value) ->
                    span("rmg-deflist__key") { +key }
                    span { +value }
                }
            }
        }
    }
}

private fun shapeRows(view: RoadmapGraphView): List<Pair<String, String>> {
    val stats = view.roadmap.getStatistics()
    val byLayer = view.roadmap.nodes.groupingBy { it.layer }.eachCount()
    val terminalFanIn = view.terminal?.let { terminal ->
        view.roadmap.edges.count { it.fromNodeId == terminal.projectId }
    } ?: 0

    return buildList {
        byLayer[0]?.let { add("layer 0" to "$it projects") }
        if (stats.maxDepth > 1) {
            val rest = (1 until stats.maxDepth).mapNotNull { byLayer[it] }.distinct()
            val label = if (stats.maxDepth == 2) "layer 1" else "layers 1–${stats.maxDepth - 1}"
            add(label to if (rest.size == 1) "${rest.first()} each" else "${rest.sum()} projects")
        }
        view.terminal?.let {
            add("edges into ${shortName(it.projectName)}" to "$terminalFanIn from ${view.producerCount} farms")
        }
        val demandRows = view.terminalStats.craftRows + view.terminalStats.openQuestions
        if (demandRows > 0) add("craft + open rows" to RoadmapGraphLayout.format(demandRows.toLong()))
    }
}

/** "Storage System YAMS" is too long for a 320px aside; the last word carries the identity. */
private fun shortName(name: String): String =
    if (name.length <= 12) name else name.split(" ").last()

// ---- 3. the graph -----------------------------------------------------------------------

private fun FlowContent.graphSection(view: RoadmapGraphView) {
    val graph = view.graph
    div("rmg-section rmg-graph") {
        div("rmg-graph__head") {
            span("rmg-label") { +"DEPENDENCY GRAPH · EDGE WEIGHT = ITEMS MOVED" }
            span("rmg-legend") {
                span { +"▬ GENERATED" }
                span { +"┄ MANUAL / BY HAND" }
                span { +"▸ TAP A NODE TO FOCUS" }
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
            span("rmg-note") { +"no generated or manual edges — do them whenever" }
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
            span("rmg-note") { +"done, and still feeding the roadmap · items · edges" }
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
