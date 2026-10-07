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
import app.mcorg.pipeline.world.roadmap.HandListSplit
import app.mcorg.pipeline.world.roadmap.StoppedFarm
import app.mcorg.presentation.templated.dsl.BadgeStatus
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.badge
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.templated.dsl.progressBar
import app.mcorg.presentation.templated.dsl.statusBadge
import app.mcorg.presentation.templated.dsl.WorldTab
import app.mcorg.presentation.templated.dsl.worldBar
import app.mcorg.presentation.templated.dsl.pages.newProjectAffordance
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.SPAN
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
    /** The farms still to build, as a set (MCO-571). Empty when everything upstream is built. */
    val toBuild: RoadmapToBuild.ToBuild,
    /**
     * Whether any finished farm feeds a drawn final project. Without one there is no supply column,
     * and the panels are drawn as a row with no graph around them (MCO-544, frame 5A).
     */
    val producing: Boolean = true,
    val startHere: RoadmapNode?,
    val startHereNote: String?,
    val producerCount: Int,
    /** Farms in the supply column that feed more than one drawn final project — "16 · 2 feed more than one". */
    val sharedProducers: Int = 0,
    /** Every final project in the world, drawn or past the cap — "3, none first". */
    val finalProjectCount: Int = 0,
    /** What is left by hand across the drawn panels — the graph's by-hand node, or 5A's strip. */
    val handTotals: RoadmapGraphLayout.HandGathered? = null,
    /** Final projects past the panel cap — listed under the graph, since no other view does now. */
    val hiddenTerminals: List<RoadmapNode> = emptyList(),
    /**
     * For a total order (5B): the drawn final projects' hand list with each prefix of the order
     * built, step by step. Null where it could not be measured.
     */
    val chainLeftAfter: List<Long?> = emptyList(),
    /** The drawn final projects' hand list now — the "before any of them" row. */
    val byHandNow: Long = 0,
    /** Projects with anything collected or any task done — "building" rather than "planned". */
    val started: Set<Int> = emptySet(),
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
 * The design's whole claim is that **sequence and supply are different questions**. The work —
 * what to finish, the order that really exists among the farms still to build, and every one of
 * them — comes first; the graph below it is supply only, finished farms feeding the final
 * projects. Nothing unbuilt enters the graph's column. See [RoadmapGraphLayout] for its geometry.
 *
 * One page whatever state the world is in (MCO-544): a world with nothing producing has the same
 * sections in the same order, and only the graph changes — no column, the panels as a row (5A).
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
            // The title sits outside the card, as on every other page
            // (MCO-505). It used to be an `.rmg-section` within it. It heads an empty world
            // too — the Projects tab heads itself in both states, and a Roadmap tab that
            // dropped its heading when empty made the two tabs look like different pages.
            roadmapTitle(headerMeta(view))
            if (view.roadmap.isEmpty()) {
                // This is the view a world opens on, so it is the first thing a new world
                // shows — and it used to be an empty card with four empty sections in it.
                // The world's one empty state answers it instead (see [worldEmptyState]).
                worldEmptyState(view.roadmap.worldId)
            } else {
                // Above the card: a loop makes any order on the page an assumption, so the page
                // asks before it draws. It used to draw the assumed order as settled and leave the
                // question to the old table view alone.
                cycleSection(view.roadmap)
                div("rmg-card") {
                    id = "roadmap-graph"
                    startHereSection(view)
                    // The work, then the picture of it (frames 4B/4C): what to finish, the order that
                    // really exists among the farms still to build, every one of them, and then the
                    // graph — whose promised-supply tab links back up to the table.
                    view.toBuild.takeIf { !it.isEmpty }?.let { toBuild ->
                        finishFirstSection(view, toBuild)
                        orderBandSection(view, toBuild)
                        toBuildSection(view, toBuild)
                    }
                    graphSection(view)
                    moreFinalProjectsSection(view)
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

/**
 * "Forever world · 16 producing farms · 6 farms to build · 3 final projects", as 4A and 5A head it.
 *
 * Producing farms are counted even at zero — "0 producing farms" is the fact a fresh world is
 * about. The project count and the layer count are gone: the first counted rows of every kind as
 * one, and depth was the queue's measure, which the page no longer draws.
 */
private fun headerMeta(view: RoadmapGraphView): String = buildList {
    add(view.roadmap.worldName)
    // An empty world has none of the counts below; "0 projects" is the one fact it has, and what the
    // Projects tab says.
    if (view.roadmap.isEmpty()) add("0 projects")
    if (view.finalProjectCount > 0 || view.producerCount > 0) {
        add("${view.producerCount} producing ${if (view.producerCount == 1) "farm" else "farms"}")
    }
    view.toBuild.rows.size.takeIf { it > 0 }?.let {
        add("$it ${if (it == 1) "farm" else "farms"} to build")
    }
    if (view.finalProjectCount > 0) {
        add("${view.finalProjectCount} final ${if (view.finalProjectCount == 1) "project" else "projects"}")
    }
}.joinToString(" · ")

// ---- 2. start here + graph shape --------------------------------------------------------

private fun FlowContent.startHereSection(view: RoadmapGraphView) {
    val start = view.startHere ?: return
    div("rmg-section rmg-start") {
        div("rmg-start__main") {
            sectionLabel { +"START HERE" }
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
        sectionLabel { +"AT A GLANCE" }
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
    val chain = toBuild.chain
    // Nothing started, and the farms form one line: the first of it is where to start (frame 5B).
    // Earned by order alone, which needs no prices — unlike ranking the set by what it saves.
    if (active.isEmpty() && chain != null) {
        startOfChainSection(view, chain)
        return
    }
    div("rmg-section rmg-start") {
        div("rmg-start__main") {
            sectionLabel { +if (active.isEmpty()) "NOTHING STARTED" else "FINISH THESE FIRST" }
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
    toBuild.chain?.let { chain ->
        orderListSection(view, toBuild, chain)
        return
    }
    val band = toBuild.band ?: return
    val others = toBuild.rows.size - toBuild.ordered.size
    val note = buildString {
        if (others > 0) {
            append("Only farms with an edge between them are drawn here.")
            append(" The other ${RoadmapToBuild.countWord(others)} ")
            append(if (others == 1) "sits" else "sit")
            append(" in the table below and nothing orders them.")
        }
        if (toBuild.bandHidden > 0) {
            if (isNotEmpty()) append(" ")
            append("${toBuild.bandHidden} more in order did not fit; the table lists them under the farm they wait on.")
        }
    }
    div("rmg-section rmg-order") {
        div("rmg-order__head") {
            val chains = if (toBuild.chains == 1) {
                "1 CHAIN OF ${toBuild.ordered.size}"
            } else {
                "${toBuild.chains} CHAINS · ${toBuild.ordered.size} FARMS"
            }
            sectionLabel { +"ORDER AMONG THE FARMS YOU'RE BUILDING · $chains" }
            if (others > 0) {
                span("rmg-note") {
                    +"the other ${RoadmapToBuild.countWord(others)} ${if (others == 1) "is" else "are"} in any order"
                }
            }
        }
        graphPanel(view, band, markerId = "rmg-order-arrow") {
            band.note?.takeIf { note.isNotEmpty() }?.let { place ->
                div("rmg-panel__note") {
                    attributes["style"] = "left: ${place.x}px; top: ${place.y}px; width: ${place.width}px"
                    +note
                }
            }
        }
        if (band.note == null && note.isNotEmpty()) p("rmg-order__note") { +note }
    }
}

/** START HERE, for a total order with nothing started: the farm the others wait on in turn (5B). */
private fun FlowContent.startOfChainSection(view: RoadmapGraphView, chain: List<RoadmapToBuild.Row>) {
    val first = chain.first()
    val others = chain.size - 1
    val unordered = view.toBuild.rows.size - chain.size
    div("rmg-section rmg-start") {
        div("rmg-start__main") {
            sectionLabel { +"START HERE" }
            div("rmg-start__name-row") {
                a(classes = "rmg-start__name") {
                    href = "/worlds/${view.roadmap.worldId}/projects/${first.projectId}"
                    +first.name
                }
                badge("1 of ${chain.size} in order")
            }
            val wait = if (others == 1) "waits" else "wait"
            p("rmg-start__note") {
                +"Nothing is ahead of it, and the other ${RoadmapToBuild.countWord(others)} $wait on it in turn."
            }
            if (unordered > 0) {
                val rest = if (unordered == 1) "farm to build is" else "farms to build are"
                p("rmg-start__note") { +"The other ${RoadmapToBuild.countWord(unordered)} $rest in any order." }
            }
        }
        glanceAside(view)
    }
}

/**
 * ORDER AMONG THE FARMS YOU'RE BUILDING, as a numbered list (frame 5B).
 *
 * When every ordered farm has at most one farm immediately before it and one after, the order is
 * total, and a total order is a numbering — so here `#` is right for the reason a partial order's
 * band refuses it. A list has no column cap, so every step shows, and the hand list left after
 * each step can sit beside it: "left after" only means something along an order.
 */
private fun FlowContent.orderListSection(
    view: RoadmapGraphView,
    toBuild: RoadmapToBuild.ToBuild,
    chain: List<RoadmapToBuild.Row>,
) {
    val f = RoadmapGraphLayout::format
    // "→ YAMS" when the line's last farm feeds one final project, which is what it is the order *to*.
    // Earlier steps often feed only the farm after them, so their supplies cannot decide it.
    val destination = chain.last().supplies.singleOrNull()
        ?.takeIf { name -> view.terminals.any { it.projectName == name } }
    val others = toBuild.rows.size - chain.size
    div("rmg-section rmg-order") {
        div("rmg-order__head") {
            val to = destination?.let { " → ${it.uppercase()}" } ?: ""
            sectionLabel { +"ORDER AMONG THE FARMS YOU'RE BUILDING · 1 CHAIN OF ${chain.size}$to" }
            span("rmg-note") {
                +if (others > 0) {
                    "a total order, so numbered — the other ${RoadmapToBuild.countWord(others)} are in any order"
                } else {
                    "a total order, so numbered"
                }
            }
        }
        ariaTable("rmg-orderlist", "The order to build in") {
            ariaRow("rmg-orderlist__row rmg-orderlist__row--head") {
                ariaCell(header = true) { +"#" }
                ariaCell(header = true) { +"FARM" }
                ariaCell(header = true) { +"WAITS ON" }
                ariaCell("rmg-tobuild__num", header = true) { +"HAND LIST LEFT AFTER" }
            }
            ariaRow("rmg-orderlist__row rmg-orderlist__row--before") {
                ariaCell {}
                ariaCell("rmg-tone-muted") { +"before any of them" }
                ariaCell {}
                ariaCell("rmg-tobuild__num") { +f(view.byHandNow) }
            }
            chain.forEachIndexed { index, row ->
                val band = if (index % 2 == 0) " rmg-tobuild__row--band" else ""
                val first = if (index == 0) " rmg-orderlist__row--first" else ""
                ariaRow("rmg-orderlist__row$band$first") {
                    ariaCell("rmg-tone-muted") { +"${index + 1}" }
                    ariaCell {
                        a(classes = "rmg-tobuild__link") {
                            href = "/worlds/${view.roadmap.worldId}/projects/${row.projectId}"
                            +row.name
                        }
                    }
                    ariaCell("rmg-tone-muted") { +if (index == 0) "nothing" else "↑ ${chain[index - 1].name}" }
                    ariaCell("rmg-tobuild__num") {
                        +(view.chainLeftAfter.getOrNull(index)?.let(f) ?: "—")
                    }
                }
            }
            destination?.let { name ->
                val final = view.terminals.first { it.projectName == name }
                val eitherWay = view.terminalStats[final.projectId]?.split?.eitherWay
                ariaRow("rmg-orderlist__row rmg-orderlist__row--final") {
                    ariaCell("rmg-tone-muted") { +"→" }
                    ariaCell {
                        a(classes = "rmg-tobuild__link rmg-orderlist__final") {
                            href = "/worlds/${view.roadmap.worldId}/projects/${final.projectId}"
                            +name
                        }
                    }
                    ariaCell("rmg-tone-muted") { +"final project" }
                    ariaCell("rmg-tobuild__num rmg-tone-muted") {
                        +(eitherWay?.let { "${f(it)} yours either way" } ?: "")
                    }
                }
            }
        }
        p("rmg-order__note") { +"Each step reads the hand list with it and every step above it built." }
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
    val grouped = RoadmapToBuild.groupsOf(toBuild, view.terminals)
    div("rmg-section rmg-tobuild") {
        id = "roadmap-to-build"
        if (grouped != null) {
            groupedToBuild(view, toBuild, grouped)
            return@div
        }
        div("rmg-tobuild__head") {
            val tail = when {
                inOrder > 0 -> "$inOrder IN ORDER"
                toBuild.unsettled > 0 -> "ORDER UNSETTLED"
                else -> "ANY ORDER"
            }
            sectionLabel { +"TO BUILD · $count ${if (count == 1) "FARM" else "FARMS"} · $tail" }
            span("rmg-note") {
                +if (toBuild.chain != null) {
                    if (toBuild.chain.size == count) "numbered in the order above" else "numbered in the order above, then state and name"
                } else if (inOrder > 0) {
                    "state, then name — a chained farm sits under the one it waits on"
                } else {
                    "sorted by state, then name"
                }
            }
        }
        ariaTable("rmg-tobuild__table", "Farms to build") {
            ariaRow("rmg-tobuild__row rmg-tobuild__row--head") {
                ariaCell(header = true) { +"FARM" }
                ariaCell(header = true) { +"STATE" }
                ariaCell(header = true) { +"SUPPLIES" }
                ariaCell("rmg-tobuild__num", header = true) { +"ITEMS SUPPLIED" }
            }
            toBuild.rows.forEachIndexed { index, row ->
                val band = if (index % 2 == 1) " rmg-tobuild__row--band" else ""
                val depth = row.depth.coerceAtMost(3)
                ariaRow("rmg-tobuild__row$band") {
                    ariaCell("rmg-tobuild__name rmg-tobuild__name--depth-$depth") {
                        if (depth > 0) span("rmg-tobuild__chain") { +"↳" }
                        // The same number the order list gives it, so the two surfaces agree (5B).
                        row.step?.let { span("rmg-tobuild__step") { +"$it" } }
                        a(classes = "rmg-tobuild__link") {
                            href = "/worlds/${view.roadmap.worldId}/projects/${row.projectId}"
                            +row.name
                        }
                    }
                    ariaCell("rmg-tone-muted") { +row.label }
                    ariaCell("rmg-tone-muted") { +RoadmapToBuild.suppliesText(row.supplies) }
                    ariaCell("rmg-tobuild__num${if (row.singleItem != null) " rmg-tobuild__num--named" else ""}") {
                        +RoadmapToBuild.itemsText(row)
                    }
                }
                // Both lines when both are true: a farm in an unanswered loop can still wait on a farm
                // outside it, and the indent alone does not say for what.
                if (row.waitsOn.isNotEmpty()) {
                    ariaRow("rmg-tobuild__sub rmg-tobuild__sub--depth-$depth$band") {
                        ariaCell(span = 4) { +RoadmapToBuild.waitsText(row.waitsOn) }
                    }
                }
                if (row.unsettled) {
                    ariaRow("rmg-tobuild__sub rmg-tobuild__sub--depth-$depth$band") {
                        ariaCell(span = 4) {
                            a(classes = "rmg-tone-amber rmg-tobuild__unsettled") {
                                href = "#roadmap-cycles"
                                +"⚠ order unsettled — which comes first is the question above"
                            }
                        }
                    }
                }
            }
            // A total of one row is that row again.
            if (count > 1) {
                ariaRow("rmg-tobuild__row rmg-tobuild__row--total") {
                    ariaCell { +if (count == 2) "both" else "all ${RoadmapToBuild.countWord(count)}" }
                    ariaCell {}
                    ariaCell {}
                    ariaCell("rmg-tobuild__num") { +RoadmapGraphLayout.format(toBuild.totalItems) }
                }
            }
        }
        itemsCaveat()
    }
}

private fun FlowContent.itemsCaveat() {
    p("rmg-tobuild__caveat") {
        span("rmg-tone-amber") { +"⚠ " }
        span("rmg-tobuild__caveat-term") { +"Items supplied" }
        +" sums supply lines, so it mixes crafted items with the raw ones underneath them — a farm's "
        +"total can count ingots that were never on the hand list. It says how much of the plan a farm "
        +"carries, not what gathering it would cost you, which is why the table is not sorted by it."
    }
}

/**
 * TO BUILD when state no longer separates anything (frame 5A): every farm planned, none ordered.
 *
 * Grouped by where each farm's output goes, then name, with no STATE column — every row would read
 * the same — and no SUPPLIES column, which the group heading now says once. The STATE column
 * returns with the first started farm, which is also when [RoadmapToBuild.groupsOf] stops grouping.
 */
private fun FlowContent.groupedToBuild(
    view: RoadmapGraphView,
    toBuild: RoadmapToBuild.ToBuild,
    grouped: RoadmapToBuild.Grouped,
) {
    val count = toBuild.rows.size
    div("rmg-tobuild__head") {
        val state = grouped.stateWord.uppercase()
        val order = if (toBuild.unsettled > 0) "ORDER UNSETTLED" else "ANY ORDER"
        sectionLabel { +"TO BUILD · $count ${if (count == 1) "FARM" else "FARMS"} · ALL $state · $order" }
        span("rmg-note") { +"one state, so grouped by where the output goes, then name" }
    }
    ariaTable("rmg-tobuild__table rmg-tobuild__table--grouped", "Farms to build, by what they feed") {
        ariaRow("rmg-tobuild__row rmg-tobuild__row--head") {
            ariaCell(header = true) { +"FARM" }
            ariaCell("rmg-tobuild__num", header = true) { +"ITEMS SUPPLIED" }
        }
        grouped.groups.forEach { group ->
            ariaRow("rmg-tobuild__group") {
                ariaCell(span = 2, rowHeader = true) {
                    +"FEEDS ${RoadmapToBuild.suppliesText(group.supplies).uppercase()} · ${group.rows.size}"
                }
            }
            group.rows.forEachIndexed { index, row ->
                val band = if (index % 2 == 1) " rmg-tobuild__row--band" else ""
                ariaRow("rmg-tobuild__row$band") {
                    ariaCell("rmg-tobuild__name") {
                        a(classes = "rmg-tobuild__link") {
                            href = "/worlds/${view.roadmap.worldId}/projects/${row.projectId}"
                            +row.name
                        }
                    }
                    ariaCell("rmg-tobuild__num${if (row.singleItem != null) " rmg-tobuild__num--named" else ""}") {
                        +RoadmapToBuild.itemsText(row)
                    }
                }
                if (row.unsettled) {
                    ariaRow("rmg-tobuild__sub rmg-tobuild__sub--depth-0$band") {
                        ariaCell(span = 2) {
                            a(classes = "rmg-tone-amber rmg-tobuild__unsettled") {
                                href = "#roadmap-cycles"
                                +"⚠ order unsettled — which comes first is the question above"
                            }
                        }
                    }
                }
            }
        }
        // Stated rather than omitted, as the supply column states them: a final project nothing
        // here feeds on its own is a fact about the world, not a group the page forgot.
        if (grouped.emptyFinals.isNotEmpty()) {
            ariaRow("rmg-tobuild__group rmg-tobuild__group--empty") {
                ariaCell(span = 2) {
                    +grouped.emptyFinals.joinToString("  ·  ") { "FEEDS ${it.uppercase()} ONLY · none" }
                }
            }
        }
    }
    itemsCaveat()
}

// The TO BUILD table is a CSS grid of divs, so these roles are what make it a table to a screen
// reader: navigable by row and column, each cell announced with its column header. It inherited
// that job from the Table view's real <table> when that view was removed (MCO-529).

private inline fun FlowContent.ariaTable(classes: String, label: String, crossinline block: DIV.() -> Unit) =
    div(classes) {
        attributes["role"] = "table"
        attributes["aria-label"] = label
        block()
    }

private inline fun DIV.ariaRow(classes: String, crossinline block: DIV.() -> Unit) =
    div(classes) {
        attributes["role"] = "row"
        block()
    }

private inline fun DIV.ariaCell(
    classes: String? = null,
    header: Boolean = false,
    rowHeader: Boolean = false,
    span: Int = 1,
    crossinline block: SPAN.() -> Unit,
) = span(classes) {
    attributes["role"] = when {
        header -> "columnheader"
        rowHeader -> "rowheader"
        else -> "cell"
    }
    if (span > 1) attributes["aria-colspan"] = span.toString()
    block()
}

/**
 * Facts about the *world*, in the four rows 4A's GRAPH SHAPE and 5A's AT A GLANCE share: what
 * produces, what is left to build and in what order, how many final projects, and what stays
 * yours whatever you build.
 *
 * "still to build N of M projects" is gone with this: it counted project *stage*, so beside
 * "farms to build" it disagreed whenever state and stage did. "longest chain" went with the queue
 * it measured, and "feeding" with the column total it repeated.
 */
private fun shapeRows(view: RoadmapGraphView): List<Pair<String, String>> = buildList {
    add(
        "producing farms" to when {
            view.producerCount == 0 -> "none yet"
            view.sharedProducers > 0 -> "${view.producerCount} · ${view.sharedProducers} feed more than one"
            else -> "${view.producerCount}"
        }
    )
    val toBuild = view.toBuild
    val inOrder = toBuild.ordered.size
    add(
        "farms to build" to when {
            toBuild.isEmpty -> "none"
            inOrder > 0 -> "${toBuild.rows.size}, $inOrder in order"
            toBuild.unsettled > 0 -> "${toBuild.rows.size}, order unsettled"
            else -> "${toBuild.rows.size}, any order"
        }
    )
    // "none first": final projects are sinks by definition, so none waits on another. Said anyway,
    // because "3 final projects" next to a numbered list invites the reader to look for an order.
    if (view.finalProjectCount > 0) {
        add("final projects" to if (view.finalProjectCount == 1) "1" else "${view.finalProjectCount}, none first")
    }
    // Summed over the drawn panels, a panel nothing is promised to counting whole — as frame 4A's
    // GRAPH SHAPE adds it up. Only when something is promised; otherwise it is just "by hand".
    val splits = view.terminals.mapNotNull { view.terminalStats[it.projectId]?.split }
    if (splits.size == view.terminals.size && splits.any { it.promised > 0 }) {
        add("yours either way" to "${RoadmapGraphLayout.format(splits.sumOf { it.eitherWay })} items")
    }
}

// ---- 3. the graph -----------------------------------------------------------------------

private fun FlowContent.graphSection(view: RoadmapGraphView) {
    val graph = view.graph
    // No farm produces yet, but there are final projects to report on: 5A's panel row.
    if (graph == null && view.terminals.isNotEmpty()) {
        panelRowSection(view)
        return
    }
    div("rmg-section rmg-graph") {
        div("rmg-graph__head") {
            // Nothing in the graph is a dependency: it is supply, drawn now or promised (MCO-571),
            // and its line weight is the per-panel rank MCO-566 shipped.
            sectionLabel { +"SUPPLY GRAPH · LINE WEIGHT = RANK BY ITEMS, PER PANEL" }
            span("rmg-legend") {
                span { +"▬ SUPPLYING NOW" }
                span { +"┄ PROMISED / BY HAND" }
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

        graphPanel(view, graph)
        splitNote(view)?.let { note -> p("rmg-graph__note") { +note } }

        view.manualEdgeNote?.let { note ->
            div("callout callout--info") {
                span("callout__icon") { +"i" }
                div("callout__body") { +note }
            }
        }
    }
}

/**
 * SUPPLY · NO FARM PRODUCES YET — the graph section of a world where nothing is built (frame 5A).
 *
 * What a nothing-built world lacks is the supply column, so that is the one thing that goes: the
 * section keeps its panels and loses its left half. With no column there are no ropes and no
 * rails to draw, so the panels stand as a plain row of cards rather than absolutely-positioned
 * nodes, and each carries a bar: promised and yours either way, on one scale across the row, so
 * the bars compare panels as well as halves.
 *
 * When the first farm is done the column and its ropes appear and the panels move to the graph's
 * right edge — 5A's stated trade: keeping a 760px empty column so they never move is the near-empty
 * graph this frame replaced.
 */
private fun FlowContent.panelRowSection(view: RoadmapGraphView) {
    div("rmg-section rmg-graph") {
        div("rmg-graph__head") {
            sectionLabel { +"SUPPLY · NO FARM PRODUCES YET" }
            span("rmg-legend") {
                span("rmg-legend__key") {
                    span("rmg-swatch rmg-swatch--promised") {}
                    +"PROMISED"
                }
                span("rmg-legend__key") {
                    span("rmg-swatch rmg-swatch--yours") {}
                    +"YOURS EITHER WAY"
                }
                span { +"BAR LENGTH = BY HAND NOW, SHARED SCALE" }
            }
        }

        // The by-hand node, as a strip: there is no column to stand in, and it speaks for every
        // panel at once.
        val hand = view.handTotals
        if (hand != null) {
            div("rmg-handstrip") {
                span {
                    span("rmg-handstrip__title") {
                        +if (view.terminals.size > 1) "By hand, all ${RoadmapToBuild.countWord(view.terminals.size)}" else "By hand"
                    }
                    span("rmg-tone-muted") {
                        +" · ${RoadmapGraphLayout.format(hand.items)} items · ${hand.materials} materials"
                    }
                }
                RoadmapGraphLayout.promisedShareOf(hand)?.let { share ->
                    val farms = view.toBuild.rows.size
                    span("rmg-tone-amber") {
                        +"⚠ $share% of it is promised by the ${if (farms == 1) "farm" else "${RoadmapToBuild.countWord(farms)} farms"}"
                    }
                }
                if (!view.toBuild.isEmpty) {
                    a(classes = "rmg-handstrip__link") {
                        href = "#roadmap-to-build"
                        +"SEE TABLE ▸"
                    }
                }
            }
        }

        val scale = view.terminals.maxOfOrNull { view.terminalStats[it.projectId]?.byHand ?: 0L }?.takeIf { it > 0 }
        div("rmg-panelrow") {
            view.terminals.forEachIndexed { index, terminal ->
                val stats = view.terminalStats[terminal.projectId]
                a(classes = "rmg-panelrow__card") {
                    href = "/worlds/${view.roadmap.worldId}/projects/${terminal.projectId}"
                    span("rmg-node__eyebrow rmg-tone-muted") {
                        +"FINAL PROJECT · ${index + 1} OF ${view.finalProjectCount}"
                    }
                    div("rmg-node__title") { +terminal.projectName }
                    if (stats != null && scale != null) handBar(stats, scale)
                    terminalBody(view, terminal.projectId)
                }
            }
            val hidden = view.finalProjectCount - view.terminals.size
            if (hidden > 0) {
                div("rmg-panelrow__card rmg-panelrow__card--more") {
                    div("rmg-node__title") { +"+$hidden more final ${if (hidden == 1) "project" else "projects"}" }
                    div("rmg-node__sub rmg-tone-muted") { +"listed below" }
                }
            }
        }
    }
}

/**
 * Promised then yours either way, as two lengths of one bar. Widths are computed per world and
 * are the only thing that reaches `style` — as [progressBar] does with its own width.
 */
private fun FlowContent.handBar(stats: RoadmapGraphLayout.TerminalStats, scale: Long) {
    val split = stats.split ?: HandListSplit.Split(promised = 0, eitherWay = stats.byHand)
    fun share(value: Long) = "%.2f".format(java.util.Locale.ROOT, value * 100.0 / scale)
    div("rmg-handbar") {
        if (split.promised > 0) {
            div("rmg-handbar__part rmg-swatch--promised") { attributes["style"] = "width: ${share(split.promised)}%" }
        }
        if (split.eitherWay > 0) {
            div("rmg-handbar__part rmg-swatch--yours") { attributes["style"] = "width: ${share(split.eitherWay)}%" }
        }
    }
}

/**
 * Under the graph, frame 4A's reading of the first panel's split — and why it will not match the
 * TO BUILD table, which is the first thing a careful reader checks.
 */
private fun splitNote(view: RoadmapGraphView): String? {
    val (terminal, split) = view.terminals.firstNotNullOfOrNull { terminal ->
        view.terminalStats[terminal.projectId]?.split?.takeIf { it.promised > 0 }?.let { terminal to it }
    } ?: return null
    val byHand = view.terminalStats.getValue(terminal.projectId).byHand
    val farms = view.toBuild.rows.size
    val built = if (farms == 1) {
        "once the farm you're building is finished"
    } else {
        "once the ${RoadmapToBuild.countWord(farms)} farms are built"
    }
    val f = RoadmapGraphLayout::format
    return "Of ${terminal.projectName}'s ${f(byHand)}-item hand list, ${f(split.promised)} disappears $built, " +
        "and ${f(split.eitherWay)} stays yours either way. Both are differences between two hand lists, so " +
        "they will not match the per-farm totals in the TO BUILD table, which count crafted items the hand " +
        "list never had — only the raw materials under them."
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
    // Until a farm produces for it, "from 0 farms 0" says nothing; how many of the farms being
    // built will feed it says what is coming (frame 5A).
    val feeds = if (stats.farms == 0) {
        val feeders = view.toBuild.feedersByTerminal[projectId] ?: 0
        if (feeders == 0) "no farm feeds it" else "$feeders of the ${view.toBuild.rows.size} feed it"
    } else {
        null
    }
    if (stats.percentComplete > 0) {
        div("rmg-terminal__progress") {
            progressBar(stats.percentComplete, 100, large = true)
            span("rmg-terminal__percent") { +"${stats.percentComplete}%" }
        }
        feeds?.let { div("rmg-terminal__state rmg-tone-muted") { +it } }
    } else {
        // A 0% bar is a widget reporting nothing. The state is the thing worth the line, and it
        // is what the reader of a not-yet-started build is looking for.
        div("rmg-terminal__state rmg-tone-muted") { +listOfNotNull("not started", feeds).joinToString(" · ") }
    }
    div("rmg-deflist rmg-terminal__facts") {
        if (stats.farms > 0) {
            span("rmg-deflist__key") { +"from ${stats.farms} ${if (stats.farms == 1) "farm" else "farms"}" }
            span { +RoadmapGraphLayout.format(stats.fromFarms) }
        }
        // "now", because the number moves when the farms being built come online.
        span("rmg-deflist__key") { +"by hand now" }
        span { +RoadmapGraphLayout.format(stats.byHand) }
        // Indented under "by hand now" because these two add up to it (MCO-572, frame 4A). Beside the
        // graph they are cut when nothing is promised — "yours either way" would only repeat the line
        // above. With nothing producing (5A) every panel carries them, "promised none" included, so
        // the row of panels reads as one comparison.
        stats.split?.takeIf { it.promised > 0 || !view.producing }?.let { split ->
            span("rmg-deflist__key rmg-deflist__key--sub") { +"promised" }
            span("rmg-tone-muted") { +if (split.promised > 0) RoadmapGraphLayout.format(split.promised) else "none" }
            span("rmg-deflist__key rmg-deflist__key--sub") { +"yours either way" }
            span("rmg-tone-muted") { +RoadmapGraphLayout.format(split.eitherWay) }
        }
        span("rmg-deflist__key") { +"craft steps" }
        span { +"${RoadmapGraphLayout.format(stats.craftRows.toLong())} rows" }
        // Cut rather than shown as zero: a row reading "open questions 0" reports nothing.
        if (stats.openQuestions > 0) {
            span("rmg-deflist__key") { +"open questions" }
            span("rmg-tone-amber") { +"${stats.openQuestions}" }
        }
    }
}

/**
 * The final projects past the three panels. The "+N more" node used to point at the table view for
 * them; with that gone (MCO-529) they are listed here, each a link, so none is unreachable from the
 * page that counted it.
 */
private fun FlowContent.moreFinalProjectsSection(view: RoadmapGraphView) {
    if (view.hiddenTerminals.isEmpty()) return
    div("rmg-section rmg-unchained") {
        div("rmg-unchained__head") {
            sectionLabel { +"MORE FINAL PROJECTS · ${view.hiddenTerminals.size}" }
            span("rmg-note") { +"past the three drawn above — smallest supply last" }
        }
        div("rmg-chips") {
            view.hiddenTerminals.forEach { terminal ->
                a(classes = "rmg-chip") {
                    href = "/worlds/${view.roadmap.worldId}/projects/${terminal.projectId}"
                    span("rmg-chip__name") { +terminal.projectName }
                    span("rmg-chip__note rmg-tone-muted") { +RoadmapToBuild.stateLabel(terminal.state, terminal.projectId in view.started) }
                }
            }
        }
    }
}

// ---- 4. not in any chain -----------------------------------------------------------------

private fun FlowContent.unchainedSection(view: RoadmapGraphView) {
    if (view.unchained.isEmpty()) return
    div("rmg-section rmg-unchained") {
        div("rmg-unchained__head") {
            sectionLabel { +"NOT IN ANY CHAIN · ${view.unchained.size}" }
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
            sectionLabel {
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
            sectionLabel { +"STOPPED · ${view.stopped.size}" }
            span("rmg-note") { +"decommissioned — they supply nothing now" }
        }
        div("rmg-chips") {
            view.stopped.forEach { farm ->
                a(classes = "rmg-chip") {
                    href = "/worlds/${view.roadmap.worldId}/projects/${farm.projectId}"
                    span("rmg-chip__name") { +farm.name }
                    // A hand-list difference (MCO-572): what the final projects would still gather by
                    // hand with every farm you're building finished, that this one running would take
                    // off. No number when that could not be measured — zero would claim it costs nothing.
                    val uncovered = farm.uncoveredItems
                    when {
                        uncovered == null -> {}
                        uncovered > 0 -> span("rmg-chip__note ${toneClass(Tone.AMBER)}") {
                            +"⚠ ${RoadmapGraphLayout.format(uncovered)} items no other farm covers, built or planned"
                        }
                        else -> span("rmg-chip__note ${toneClass(Tone.MUTED)}") { +"nothing it made is missing now" }
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
