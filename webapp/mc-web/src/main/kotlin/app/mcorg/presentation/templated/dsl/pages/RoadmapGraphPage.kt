package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.ManualOrdering
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapNode
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.Graph
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.GraphNode
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.NodeKind
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.Tone
import app.mcorg.pipeline.world.roadmap.RoadmapToBuild
import app.mcorg.pipeline.world.roadmap.StoppedFarm
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
    /**
     * The farms still to build, as a set (MCO-571) — non-null once anything in the world is
     * producing. Null keeps the sequence band inside the graph, as a world with nothing built
     * needs it.
     */
    val toBuild: RoadmapToBuild.ToBuild? = null,
    val startHere: RoadmapNode?,
    val startHereNote: String?,
    val producerCount: Int,
    /**
     * "34,313 items from 6 farms" — what the supply column feeds into the drawn final projects, or
     * null when nothing does. One derivation with the column, so its two numbers describe the same
     * farms: the row used to divide one panel's items by the whole world's farm count.
     */
    val feeding: String? = null,
    val producerRows: List<ProducerRow>,
    val unchained: List<UnchainedRow>,
    /** The final projects drawn as panels, largest demand first (MCO-563). */
    val terminals: List<RoadmapNode>,
    /** Each drawn final project's plan totals, by project id. */
    val terminalStats: Map<Int, RoadmapGraphLayout.TerminalStats>,
    val manualEdgeNote: String?,
    val dataGaps: List<DataGap>,
    /** Decommissioned farms and what no farm covers since they stopped — see [stoppedSection]. */
    val stopped: List<StoppedFarm> = emptyList(),
    /** The world's hand-made orderings, for § 4's roster — see [manualOrderingSection]. */
    val manualOrderings: List<ManualOrdering> = emptyList(),
    /** Edges the app derived rather than anybody typing, for the roster's "these are yours" line. */
    val generatedEdgeCount: Int = 0,
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
                // Above the card, exactly where the table view puts it: a loop makes the order the
                // graph draws an assumption, so the page asks before it draws. This view used to
                // draw the assumed order as settled and leave the question to the table alone.
                cycleSection(view.roadmap)
                div("rmg-card") {
                    id = "roadmap-graph"
                    startHereSection(view)
                    // The work, then the picture of it (frames 4B/4C): what to finish, the order that
                    // really exists among the farms still to build, every one of them, and then the
                    // graph — whose promised-supply tab links back up to the table.
                    view.toBuild?.takeIf { !it.isEmpty }?.let { toBuild ->
                        finishFirstSection(view, toBuild)
                        orderBandSection(view, toBuild)
                        toBuildSection(view, toBuild)
                    }
                    graphSection(view)
                    // Between the graph and the lists, as the design orders it: the graph is
                    // where you *see* that one hand-made edge sets the world's depth, and this
                    // is where you do something about it (MCO-302).
                    manualOrderingSection(
                        worldId = view.roadmap.worldId,
                        orderings = view.manualOrderings,
                        generatedEdgeCount = view.generatedEdgeCount,
                        projectCount = view.roadmap.nodes.size,
                        canEdit = isWorldAdmin,
                    )
                    unchainedSection(view)
                    producingSection(view)
                    stoppedSection(view)
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
        view.toBuild?.rows?.size?.takeIf { it > 0 }?.let {
            add("$it ${if (it == 1) "farm" else "farms"} to build")
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
        glanceAside(view)
    }
}

private fun FlowContent.glanceAside(view: RoadmapGraphView) {
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

// ---- 2b. finish these first, the order band, the TO BUILD table (MCO-571) ----------------

/**
 * FINISH THESE FIRST — the farms already in progress (frame 4B).
 *
 * It replaces START HERE whenever there are farms to build. START HERE named one project, which
 * ranks the set; the only ranking that needs no prices is "finish what is open before starting
 * another". With nothing started there is nothing to finish, and the section says the set is
 * unranked instead of inventing a first.
 */
private fun FlowContent.finishFirstSection(view: RoadmapGraphView, toBuild: RoadmapToBuild.ToBuild) {
    val active = toBuild.inProgress
    div("rmg-section rmg-start") {
        div("rmg-start__main") {
            span("rmg-label") { +if (active.isEmpty()) "NOTHING STARTED" else "FINISH THESE FIRST" }
            div("rmg-start__name-row rmg-finish__names") {
                if (active.isEmpty()) {
                    val count = RoadmapToBuild.countWord(toBuild.rows.size).replaceFirstChar { it.uppercase() }
                    span("rmg-finish__none") {
                        +"$count ${if (toBuild.rows.size == 1) "farm" else "farms"} to build"
                    }
                } else {
                    active.forEachIndexed { index, row ->
                        if (index > 0) {
                            span("rmg-finish__joiner") { +if (index == active.lastIndex) "and" else "," }
                        }
                        a(classes = "rmg-start__name") {
                            href = "/worlds/${view.roadmap.worldId}/projects/${row.projectId}"
                            +row.name
                        }
                    }
                    span("badge badge--in-progress") { +RoadmapToBuild.finishBadge(active.size) }
                }
            }
            RoadmapToBuild.finishNotes(toBuild).forEach { note -> p("rmg-start__note") { +note } }
        }
        glanceAside(view)
    }
}

/**
 * ORDER AMONG THE FARMS YOU'RE BUILDING — the band, back for the chain alone (frame 4C).
 *
 * Above the graph rather than inside it: its subject is the farms being built, and those are
 * deliberately not in the supply column. Absent when nothing has order, which includes a loop
 * nobody has answered — drawing the guess would answer the question for them.
 */
private fun FlowContent.orderBandSection(view: RoadmapGraphView, toBuild: RoadmapToBuild.ToBuild) {
    val band = toBuild.band ?: return
    val others = toBuild.rows.size - toBuild.ordered.size
    val note = buildString {
        append("Only farms with an edge between them are drawn here.")
        if (others > 0) {
            append(" The other ${RoadmapToBuild.countWord(others)} ")
            append(if (others == 1) "sits" else "sit")
            append(" in the table below and nothing orders them.")
        }
        if (toBuild.bandHidden > 0) {
            append(" ${toBuild.bandHidden} more in order did not fit; the table lists them under the farm they wait on.")
        }
    }
    div("rmg-section rmg-order") {
        div("rmg-order__head") {
            val chains = if (toBuild.chains == 1) {
                "1 CHAIN OF ${toBuild.ordered.size}"
            } else {
                "${toBuild.chains} CHAINS · ${toBuild.ordered.size} FARMS"
            }
            span("rmg-label") { +"ORDER AMONG THE FARMS YOU'RE BUILDING · $chains" }
            if (others > 0) {
                span("rmg-note") {
                    +"the other ${RoadmapToBuild.countWord(others)} ${if (others == 1) "is" else "are"} in any order"
                }
            }
        }
        graphPanel(view, band, markerId = "rmg-order-arrow") {
            band.note?.let { place ->
                div("rmg-panel__note") {
                    attributes["style"] = "left: ${place.x}px; top: ${place.y}px; width: ${place.width}px"
                    +note
                }
            }
        }
        if (band.note == null) p("rmg-order__note") { +note }
    }
}

/**
 * TO BUILD — every farm still to build that feeds a final project, sorted by state then name
 * (frame 4B), a chained farm indented under the one it waits on (4C).
 *
 * No hours: prices are only relatively calibrated (MCO-564), which is why this table exists in
 * place of 4A's. And no `gather instead ▸` — it has no mechanism yet: taking a farm out of the plan
 * is neither a state change nor a dismissal, and it needs a definition that survives the plan being
 * re-derived before a button can promise it (MCO-574).
 */
private fun FlowContent.toBuildSection(view: RoadmapGraphView, toBuild: RoadmapToBuild.ToBuild) {
    val count = toBuild.rows.size
    val inOrder = toBuild.ordered.size
    div("rmg-section rmg-tobuild") {
        id = "roadmap-to-build"
        div("rmg-tobuild__head") {
            val tail = when {
                inOrder > 0 -> "$inOrder IN ORDER"
                toBuild.unsettled > 0 -> "ORDER UNSETTLED"
                else -> "ANY ORDER"
            }
            span("rmg-label") { +"TO BUILD · $count ${if (count == 1) "FARM" else "FARMS"} · $tail" }
            span("rmg-note") {
                +if (inOrder > 0) {
                    "state, then name — a chained farm sits under the one it waits on"
                } else {
                    "sorted by state, then name"
                }
            }
        }
        div("rmg-tobuild__table") {
            div("rmg-tobuild__row rmg-tobuild__row--head") {
                span { +"FARM" }
                span { +"STATE" }
                span { +"SUPPLIES" }
                span("rmg-tobuild__num") { +"ITEMS SUPPLIED" }
            }
            toBuild.rows.forEachIndexed { index, row ->
                val band = if (index % 2 == 1) " rmg-tobuild__row--band" else ""
                val depth = row.depth.coerceAtMost(3)
                div("rmg-tobuild__row$band") {
                    span("rmg-tobuild__name rmg-tobuild__name--depth-$depth") {
                        if (depth > 0) span("rmg-tobuild__chain") { +"↳" }
                        a(classes = "rmg-tobuild__link") {
                            href = "/worlds/${view.roadmap.worldId}/projects/${row.projectId}"
                            +row.name
                        }
                    }
                    span("rmg-tone-muted") { +RoadmapToBuild.stateLabel(row.state) }
                    span("rmg-tone-muted") { +RoadmapToBuild.suppliesText(row.supplies) }
                    span("rmg-tobuild__num${if (row.singleItem != null) " rmg-tobuild__num--named" else ""}") {
                        +RoadmapToBuild.itemsText(row)
                    }
                }
                when {
                    row.unsettled -> div("rmg-tobuild__sub rmg-tobuild__sub--depth-$depth$band") {
                        a(classes = "rmg-tone-amber rmg-tobuild__unsettled") {
                            href = "#roadmap-cycles"
                            +"⚠ order unsettled — which comes first is the question above"
                        }
                    }
                    row.waitsOn.isNotEmpty() -> div("rmg-tobuild__sub rmg-tobuild__sub--depth-$depth$band") {
                        +RoadmapToBuild.waitsText(row.waitsOn)
                    }
                }
            }
            // A total of one row is that row again.
            if (count > 1) {
                div("rmg-tobuild__row rmg-tobuild__row--total") {
                    span { +if (count == 2) "both" else "all ${RoadmapToBuild.countWord(count)}" }
                    span {}
                    span {}
                    span("rmg-tobuild__num") { +RoadmapGraphLayout.format(toBuild.totalItems) }
                }
            }
        }
        p("rmg-tobuild__caveat") {
            span("rmg-tone-amber") { +"⚠ " }
            span("rmg-tobuild__caveat-term") { +"Items supplied" }
            +" sums supply lines, so it mixes crafted items with the raw ones underneath them — a farm's "
            +"total can count ingots that were never on the hand list. It says how much of the plan a farm "
            +"carries, not what gathering it would cost you, which is why the table is not sorted by it."
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
        val toBuild = view.toBuild
        if (toBuild != null) {
            // Depth measured the queue, and there is no queue any more (MCO-571). What replaces it is
            // the one ordering fact left: whether any of the farms still to build waits on another.
            if (!toBuild.isEmpty) {
                val inOrder = toBuild.ordered.size
                val order = when {
                    inOrder > 0 -> "$inOrder in order"
                    toBuild.unsettled > 0 -> "order unsettled"
                    else -> "any order"
                }
                add("farms to build" to "${toBuild.rows.size}, $order")
            }
        } else if (stats.maxDepth > 1) {
            add("longest chain" to "${stats.maxDepth} projects deep")
        }
        // Items, not edge count. The old row said "86 from 22 farms", where 86 was the number of
        // supply relationships — and it read as a quantity of items. Not "feeding <name>": the
        // name had to be shortened to fit a 320px aside, and the only cheap way to do that was to
        // take the last word — which gives "YAMS" for "Storage System YAMS" but "North" for
        // "Iron Farm North". The graph names the destination a few centimetres away.
        view.feeding?.let { add("feeding" to it) }
    }
}

// ---- 3. the graph -----------------------------------------------------------------------

private fun FlowContent.graphSection(view: RoadmapGraphView) {
    val graph = view.graph
    div("rmg-section rmg-graph") {
        div("rmg-graph__head") {
            // With no band, nothing in the graph is a dependency any more: it is supply, drawn now
            // or promised (MCO-571), and its line weight is the per-panel rank MCO-566 shipped.
            if (view.toBuild != null) {
                span("rmg-label") { +"SUPPLY GRAPH · LINE WEIGHT = RANK BY ITEMS, PER PANEL" }
                span("rmg-legend") {
                    span { +"▬ SUPPLYING NOW" }
                    span { +"┄ PROMISED / BY HAND" }
                    span { +"▸ TAP A PROJECT TO OPEN IT" }
                }
            } else {
                span("rmg-label") { +"DEPENDENCY GRAPH · LINE WEIGHT = ITEMS MOVED" }
                span("rmg-legend") {
                    span { +"▬ GENERATED" }
                    span { +"┄ MANUAL / BY HAND" }
                    span { +"▸ TAP A PROJECT TO OPEN IT" }
                }
            }
        }

        if (graph == null) {
            div("rmg-graph__empty") {
                +"No project in this world supplies another yet, so there is no chain to draw. "
                +"Define some resources and the graph fills in."
            }
            return@div
        }

        graphPanel(view, graph)

        view.manualEdgeNote?.let { note ->
            div("callout callout--info") {
                span("callout__icon") { +"i" }
                div("callout__body") { +note }
            }
        }
    }
}

/**
 * One absolutely-positioned panel: edges underneath, nodes on top. Shared by the graph and by the
 * order band above it, which is the same vocabulary drawn about a different set of projects.
 */
private fun FlowContent.graphPanel(
    view: RoadmapGraphView,
    graph: Graph,
    /** One per panel: two SVGs on the page each defining `rmg-arrow` would duplicate an id. */
    markerId: String = "rmg-arrow",
    extra: FlowContent.() -> Unit = {},
) {
    div("rmg-panel") {
        attributes["style"] = "width: ${graph.width}px; height: ${graph.height}px"
        edgeSvg(graph, markerId)
        // Document order is promised tab → sequence → terminal → each group with its own nodes.
        // Desktop ignores it (everything is absolutely positioned); the mobile fallback drops the
        // positioning and reads exactly this order, which is the linear queue the design asks for
        // below 768px.
        graph.nodes
            .filter { it.kind == NodeKind.PROMISED }
            .forEach { node -> graphNode(view, node) }
        graph.nodes
            .filter { it.kind == NodeKind.START || it.kind == NodeKind.SEQUENCE }
            .forEach { node -> graphNode(view, node) }
        graph.nodes
            .filter { it.kind == NodeKind.TERMINAL }
            .forEach { node -> graphNode(view, node) }
        graph.groups.forEach { group ->
            groupHeader(group)
            graph.nodes
                .filter { it.group == group.key }
                .forEach { node -> graphNode(view, node) }
        }
        graph.bandCaption?.let { caption ->
            div("rmg-band-caption") {
                attributes["style"] = "left: ${caption.x}px; top: ${caption.y}px"
                +caption.text
            }
        }
        extra()
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
private fun FlowContent.edgeSvg(graph: Graph, markerId: String) {
    val paths = graph.edges.joinToString("\n") { edge ->
        val dash = if (edge.dashed) """ stroke-dasharray="5 4"""" else ""
        // A rope landing on an intake rail carries no arrowhead: the rail takes the one arrow
        // into the panel, which is what stopped seven of them piling up on its left edge.
        val marker = if (edge.marker) """ marker-end="url(#$markerId)"""" else ""
        val label = edge.label?.let {
            """<text x="${it.x}" y="${it.y}" class="rmg-edge__label" text-anchor="${it.anchor}">${escape(it.text)}</text>"""
        } ?: ""
        """<path d="${escape(edge.path)}" class="rmg-edge" stroke-width="${edge.strokeWidth}"$dash$marker></path>$label"""
    }

    // After the ropes, so the dots sit on top of what lands on them.
    val rails = graph.rails.joinToString("\n") { rail ->
        val line = if (rail.dots.size > 1) {
            """<path d="M ${rail.x} ${rail.top} L ${rail.x} ${rail.bottom}" class="rmg-rail"></path>"""
        } else {
            ""
        }
        val dots = rail.dots.joinToString("") {
            """<circle cx="${rail.x}" cy="$it" r="3" class="rmg-rail__dot"></circle>"""
        }
        val label =
            """<text x="${rail.x}" y="${rail.labelY}" class="rmg-rail__label" text-anchor="end">${escape(rail.label)}</text>"""
        "$line$dots$label"
    }

    consumer.onTagContentUnsafe {
        +"""
        <svg class="rmg-panel__edges" viewBox="0 0 ${graph.width} ${graph.height}" width="${graph.width}" height="${graph.height}" aria-hidden="true">
          <defs>
            <marker id="$markerId" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" class="rmg-edge__head"></path>
            </marker>
          </defs>
          $paths
          $rails
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
        // A final project's panel carries its own numbers; the "+N more" node carries none.
        if (node.kind == NodeKind.TERMINAL && node.projectId != null) terminalBody(view, node.projectId)
    }

    if (node.kind == NodeKind.PROMISED) {
        // The tab stands for the TO BUILD table, so it links there rather than to any one project.
        a(classes = "rmg-node ${kindClass(node.kind)}") {
            href = "#roadmap-to-build"
            attributes["style"] = geometry
            span("rmg-node__title") { +node.title }
            node.subLines.forEach { line -> span("rmg-node__sub ${toneClass(line.tone)}") { +line.text } }
        }
    } else if (node.projectId != null) {
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

private fun FlowContent.terminalBody(view: RoadmapGraphView, projectId: Int) {
    val stats = view.terminalStats[projectId] ?: return
    if (stats.percentComplete > 0) {
        div("rmg-terminal__progress") {
            progressBar(stats.percentComplete, 100, large = true)
            span("rmg-terminal__percent") { +"${stats.percentComplete}%" }
        }
    } else {
        // A 0% bar is a widget reporting nothing. The state is the thing worth the line, and it
        // is what the reader of a not-yet-started build is looking for.
        div("rmg-terminal__state rmg-tone-muted") { +"not started" }
    }
    div("rmg-deflist rmg-terminal__facts") {
        span("rmg-deflist__key") { +"from ${stats.farms} ${if (stats.farms == 1) "farm" else "farms"}" }
        span { +RoadmapGraphLayout.format(stats.fromFarms) }
        // "now", because the number moves when the farms being built come online — the split that
        // says how much of it does is the next piece of this frame.
        span("rmg-deflist__key") { +"by hand now" }
        span { +RoadmapGraphLayout.format(stats.byHand) }
        span("rmg-deflist__key") { +"craft steps" }
        span { +"${RoadmapGraphLayout.format(stats.craftRows.toLong())} rows" }
        // Cut rather than shown as zero: a row reading "open questions 0" reports nothing.
        if (stats.openQuestions > 0) {
            span("rmg-deflist__key") { +"open questions" }
            span("rmg-tone-amber") { +"${stats.openQuestions}" }
        }
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

// ---- 6. stopped --------------------------------------------------------------------------

/**
 * Decommissioned farms (MCO-541), and what no farm covers since they stopped.
 *
 * The roadmap's edges drop a stopped farm — right for the graph, since nothing waits on it — and
 * that left the page with no trace of it: decommissioning Forever world's two largest farms nearly
 * tripled the hand list on a page that never said why. A farm whose output another still makes is
 * listed too, because "stopping it cost nothing" is also worth knowing.
 */
private fun FlowContent.stoppedSection(view: RoadmapGraphView) {
    if (view.stopped.isEmpty()) return
    div("rmg-section rmg-stopped") {
        div("rmg-stopped__head") {
            span("rmg-label") { +"STOPPED · ${view.stopped.size}" }
            span("rmg-note") { +"decommissioned — they supply nothing now" }
        }
        div("rmg-chips") {
            view.stopped.forEach { farm ->
                a(classes = "rmg-chip") {
                    href = "/worlds/${view.roadmap.worldId}/projects/${farm.projectId}"
                    span("rmg-chip__name") { +farm.name }
                    if (farm.uncoveredItems > 0) {
                        span("rmg-chip__note ${toneClass(Tone.AMBER)}") {
                            +"⚠ ${RoadmapGraphLayout.format(farm.uncoveredItems)} items no farm covers now"
                        }
                    } else {
                        span("rmg-chip__note ${toneClass(Tone.MUTED)}") { +"nothing it made is missing now" }
                    }
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
    NodeKind.PROMISED -> "rmg-node--promised"
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
