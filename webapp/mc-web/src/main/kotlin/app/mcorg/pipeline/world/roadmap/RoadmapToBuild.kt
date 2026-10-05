package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapNode
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.GraphEdge
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.GraphNode
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.NodeKind
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.SubLine
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.Tone
import app.mcorg.pipeline.world.roadmap.RoadmapGraphLayout.format

/**
 * The farms still to build, as a **set** rather than a queue (MCO-571, design frames 4B and 4C).
 *
 * Once a world has farms producing, nothing upstream of its final projects is usually waiting on
 * anything else: the six unbuilt farms of a mature world each feed a build directly, and drawing
 * them as a left-to-right band asserted an order that did not exist. They leave the graph and
 * become a table — sorted by state, then name, because without calibrated prices (MCO-564) there
 * is no honest way to rank them by what they save.
 *
 * **Order survives only where an edge says so.** Two unbuilt farms with an edge between them do
 * have an order, and that chain alone comes back as a band above the graph (4C). A partial order
 * is not a numbering, so the table never gets a `#` column: it indents a chained farm under the
 * one it waits on instead.
 *
 * **An unanswered loop is not an order.** [RoadmapCycles] sets one edge of a balanced loop aside
 * so the page can render, and that guess leaves a perfectly drawable chain behind. Drawing it
 * would answer the question the page is asking above the card, so the farms in an unanswered
 * loop read `⚠ order unsettled` instead, and nothing between them is indented or banded.
 *
 * Pure, like [RoadmapGraphLayout]: one derivation over the [Roadmap]'s own edges, so the table,
 * the band and the graph's promised-supply ropes cannot disagree about which farms are in play.
 */
object RoadmapToBuild {

    // ---- band geometry, on the band panel's own coordinate system --------------------------

    /** The card's content width: the band sits inside a section, not edge to edge. */
    private const val BAND_WIDTH = 976

    private const val BAND_LEFT = 12
    private const val BAND_TOP = 34
    private const val BAND_NODE_WIDTH = 240
    private const val BAND_NODE_HEIGHT = 56
    private const val BAND_PITCH = 62

    /** Wide enough between two columns for "3,787 Cobblestone" to sit over the arrow. */
    private const val BAND_GAP = 116

    /** The tighter gap a fourth column forces; its labels are dropped rather than clipped. */
    private const val BAND_GAP_TIGHT = 56
    private const val BAND_BOTTOM_PADDING = 18

    /** Between one row of columns and the next, when the depth wraps. */
    private const val BAND_BLOCK_GAP = 28
    private const val MAX_BAND_STROKE = 2.2

    /** Room the in-band note needs beside the last column before it moves below the panel. */
    private const val BAND_NOTE_MIN_WIDTH = 260

    /**
     * The band is bounded like every other strip on the page. A chain deeper than this, or a
     * column taller, still lists every farm in the table; only the drawing stops.
     */
    const val MAX_BAND_COLUMNS = 4
    const val MAX_BAND_ROWS = 4
    const val MAX_BAND_BLOCKS = 3

    // ---- outputs -------------------------------------------------------------------------

    /** One settled thing a farm waits on: another farm still to build. */
    data class Wait(val projectId: Int, val name: String, val itemName: String?, val quantity: Long?)

    data class Row(
        val projectId: Int,
        val name: String,
        val state: ProjectState,
        /**
         * Whether anything has been collected for it or any of its tasks done. "Building" is read
         * from this rather than from [state]: a project's state is set by the door it came in
         * through, and every import arrives ACTIVE whether or not anyone has touched it (MCO-579).
         */
        val started: Boolean,
        /** What it feeds: the final projects, in panel order, or the farms when it feeds none. */
        val supplies: List<String>,
        /** Sum of the supply lines to [supplies]. Mixes crafted and raw items — see the table's note. */
        val items: Long,
        /** The one material, when the farm sends exactly one — "85 Prismarine Shard" says more than "85". */
        val singleItem: String?,
        /** Indentation: 0 for a farm that waits on nothing still to build. */
        val depth: Int,
        /** Everything it waits on, the claims an earlier farm already covers included. */
        val waitsOn: List<Wait>,
        /** In a loop nobody has answered yet, so it has no order to show. */
        val unsettled: Boolean,
        /** Its place in a total order — 1 to n — or null when the farms to build have none. */
        val step: Int? = null,
    ) {
        /** "◐ building", "‖ paused" or "○ planned", from what has been done rather than declared. */
        val label: String get() = progressLabel(state, started)
    }

    /**
     * The farms still to build, and the order among them.
     *
     * [rows] is in table order. [promisedByTerminal] is what all of them together send each final
     * project — the one dashed rope per panel from the graph's promised-supply tab.
     */
    data class ToBuild(
        val rows: List<Row>,
        val promisedByTerminal: Map<Int, Long>,
        /** Farms with an edge to or from another farm still to build. */
        val ordered: Set<Int>,
        /** Weakly connected pieces of [ordered] — "1 CHAIN OF 2". */
        val chains: Int,
        /** The order drawn as columns by depth; null when there is none, or when it is a [chain]. */
        val band: RoadmapGraphLayout.Graph?,
        /** Ordered farms the band had no room for; the table still lists them. */
        val bandHidden: Int,
        /** How many of [rows] feed each final project directly, by project id — "16 of the 22 feed it". */
        val feedersByTerminal: Map<Int, Int> = emptyMap(),
        /**
         * Every ordered farm, in order, when they form a single line (frame 5B): each has at most one
         * farm immediately before it and one after. A total order is a numbering, so the page lists
         * it with a `#` instead of drawing columns. Null for a partial order or none at all.
         */
        val chain: List<Row>? = null,
    ) {
        /** Started and not paused — what FINISH THESE FIRST names. */
        val inProgress: List<Row> get() = rows.filter { it.started && it.state != ProjectState.PAUSED }
        val unsettled: Int get() = rows.count { it.unsettled }
        val totalItems: Long get() = rows.sumOf { it.items }
        val isEmpty: Boolean get() = rows.isEmpty()
    }

    // ---- the derivation ------------------------------------------------------------------

    /**
     * Every unfinished project upstream of [terminals], as the TO BUILD table, plus the order among
     * them: a band of columns for a partial order, a numbered [ToBuild.chain] for a total one.
     *
     * Upstream of *any* final project rather than only the drawn ones: a farm feeding the fourth
     * final project is still work, and would otherwise appear in no section of the page.
     *
     * @param started projects with anything collected or any task done — see [Row.started].
     */
    fun of(roadmap: Roadmap, terminals: List<RoadmapNode>, started: Set<Int> = emptySet()): ToBuild {
        val farms = RoadmapGraphLayout.unbuiltUpstreamOf(roadmap, terminals)
        val farmIds = farms.mapTo(mutableSetOf()) { it.projectId }
        val terminalIds = terminals.map { it.projectId }
        val byId = roadmap.nodes.associateBy { it.projectId }

        // Members of each unanswered loop, by loop. An edge between two farms of the same loop is
        // what the guess left behind, not an order anybody stated.
        val loops = roadmap.cycles.map { it.projectIds.toSet() }
        val unsettledIds = loops.flatMapTo(mutableSetOf()) { it }.intersect(farmIds)
        fun sameLoop(a: Int, b: Int) = loops.any { a in it && b in it }

        // One edge per ordered pair between farms still to build, the largest claim standing for
        // it — a farm needing three things from another is one thing to wait for. Blocking only: an
        // unbuilt farm whose output a running farm already covers (MCO-466) is not something to wait on.
        val orderEdges = roadmap.edges
            .filter { it.fromNodeId in farmIds && it.toNodeId in farmIds && it.fromNodeId != it.toNodeId }
            .filter { it.isBlocking }
            .filterNot { sameLoop(it.fromNodeId, it.toNodeId) }
            .groupBy { it.fromNodeId to it.toNodeId }
            .map { (_, same) -> same.maxBy { it.quantity ?: -1L } }

        val waitsOn = orderEdges.groupBy({ it.fromNodeId }) { edge ->
            Wait(edge.toNodeId, edge.toNodeName, edge.itemName, edge.quantity)
        }
        val ordered = orderEdges.flatMapTo(mutableSetOf()) { listOf(it.fromNodeId, it.toNodeId) }

        // The order's skeleton: a wait only where no longer path already says it. A tree farm that
        // waits on all five farms before it is, as an order, one step after the last of them —
        // drawn as five arrows it is a fan of crossing lines labelled with the wrong farms.
        val immediateBefore = transitiveReduction(orderEdges.map { it.fromNodeId to it.toNodeId })
        // An unanswered loop leaves the order open: a farm in it may come before the chain's first,
        // so numbering the rest would claim a total order the user has not given.
        val chainIds = if (unsettledIds.isEmpty()) totalOrderOf(ordered, immediateBefore) else null
        val orderDepth = depthsOf(ordered, immediateBefore)

        val sortKey = compareBy<RoadmapNode>(
            { progressRank(it.state, it.projectId in started) },
            { it.projectName.lowercase() },
            { it.projectId },
        )

        val supplies = farms.associate { it.projectId to suppliesOf(roadmap, it, terminalIds, farmIds) }
        val rows = mutableListOf<Row>()
        val placed = mutableSetOf<Int>()
        fun row(farm: RoadmapNode, depth: Int, step: Int?): Row {
            val supply = supplies.getValue(farm.projectId)
            return Row(
                projectId = farm.projectId,
                name = farm.projectName,
                state = farm.state,
                started = farm.projectId in started,
                supplies = supply.names,
                items = supply.items,
                singleItem = supply.singleItem,
                depth = depth,
                // Nearest first: the farm right before it leads, the ones further up follow in turn.
                waitsOn = waitsOn[farm.projectId].orEmpty().sortedWith(
                    compareByDescending<Wait> { orderDepth[it.projectId] ?: 0 }.thenBy { it.name.lowercase() }
                ),
                unsettled = farm.projectId in unsettledIds,
                step = step,
            )
        }

        if (chainIds != null) {
            // A total order: the line first, numbered, then everything nothing orders.
            chainIds.forEachIndexed { index, id ->
                placed += id
                rows += row(byId.getValue(id), depth = 0, step = index + 1)
            }
            farms.filter { it.projectId !in placed }.sortedWith(sortKey).forEach { rows += row(it, 0, null) }
        } else {
            // A partial order, or none: state then name, a chained farm under the farm immediately
            // before it.
            val parentOf = farms.associate { farm ->
                farm.projectId to immediateBefore[farm.projectId].orEmpty()
                    .mapNotNull { byId[it] }
                    .minWithOrNull(sortKey)
                    ?.projectId
            }
            val childrenOf = farms
                .filter { parentOf[it.projectId] != null }
                .groupBy { parentOf.getValue(it.projectId)!! }
                .mapValues { (_, children) -> children.sortedWith(sortKey) }
            fun place(farm: RoadmapNode, depth: Int) {
                if (!placed.add(farm.projectId)) return
                rows += row(farm, depth, null)
                childrenOf[farm.projectId].orEmpty().forEach { place(it, depth + 1) }
            }
            farms.filter { parentOf[it.projectId] == null }.sortedWith(sortKey).forEach { place(it, 0) }
            // A floor, never the path: the edges are acyclic once loops are broken, so every farm has
            // a root above it. Should one ever survive, it is listed flat rather than lost.
            farms.filter { it.projectId !in placed }.sortedWith(sortKey).forEach { place(it, 0) }
        }

        // --- promised supply, per final project ---------------------------------------------
        val promised = roadmap.edges
            .filter { it.toNodeId in farmIds && it.fromNodeId in terminalIds }
            .groupBy { it.fromNodeId }
            .mapValues { (_, edges) -> edges.sumOf { it.quantity ?: 0L } }
            .filterValues { it > 0 }

        val band = if (chainIds == null) {
            bandOf(farms.filter { it.projectId in ordered }, waitsOn, immediateBefore, farms.size, sortKey, started)
        } else {
            null
        }

        return ToBuild(
            rows = rows,
            promisedByTerminal = promised,
            ordered = ordered,
            chains = chainsOf(ordered, orderEdges.map { it.fromNodeId to it.toNodeId }),
            band = band?.graph,
            bandHidden = band?.hidden ?: 0,
            feedersByTerminal = roadmap.edges
                .filter { it.toNodeId in farmIds && it.fromNodeId in terminalIds }
                .groupBy { it.fromNodeId }
                .mapValues { (_, edges) -> edges.map { it.toNodeId }.distinct().size },
            chain = chainIds?.map { id -> rows.first { it.projectId == id } },
        )
    }

    /**
     * Each waiting farm's *immediate* predecessors: `waiter → [farms right before it]`.
     *
     * [waits] are `(waiter, waited on)` pairs over an acyclic set. A pair is dropped when another
     * farm the waiter waits on already comes after the one it names — the longer path says it.
     */
    internal fun transitiveReduction(waits: List<Pair<Int, Int>>): Map<Int, List<Int>> {
        val before = waits.groupBy({ it.first }, { it.second })
        val ancestors = mutableMapOf<Int, Set<Int>>()
        fun ancestorsOf(node: Int, visiting: MutableSet<Int> = mutableSetOf()): Set<Int> {
            ancestors[node]?.let { return it }
            // A loop cannot survive RoadmapCycles; should one, it is cut here rather than recursed.
            if (!visiting.add(node)) return emptySet()
            val result = before[node].orEmpty().flatMapTo(mutableSetOf()) { listOf(it) + ancestorsOf(it, visiting) }
            ancestors[node] = result
            return result
        }
        return before.mapValues { (waiter, direct) ->
            direct.filter { candidate ->
                direct.none { other -> other != candidate && candidate in ancestorsOf(other) }
            }.distinct()
        }.filterValues { it.isNotEmpty() }
    }

    /**
     * Each farm's longest path from a farm that waits on nothing — Kahn's order over an acyclic
     * graph. A farm a surviving loop leaves unplaced sits at 0 rather than being lost.
     */
    internal fun depthsOf(ids: Collection<Int>, immediateBefore: Map<Int, List<Int>>): Map<Int, Int> {
        val before = ids.associateWith { id -> immediateBefore[id].orEmpty().filter { it in ids } }
        val depth = mutableMapOf<Int, Int>()
        var pending = ids.toList()
        while (pending.isNotEmpty()) {
            val ready = pending.filter { id -> before.getValue(id).all { it in depth } }
            if (ready.isEmpty()) break
            ready.forEach { id -> depth[id] = (before.getValue(id).maxOfOrNull { depth.getValue(it) + 1 } ?: 0) }
            pending = pending - ready.toSet()
        }
        pending.forEach { depth[it] = 0 }
        return depth
    }

    /**
     * [ordered] as one line, first to last, or null when the order is not total: some farm has two
     * farms immediately before or after it, or the farms make more than one chain.
     */
    internal fun totalOrderOf(ordered: Set<Int>, immediateBefore: Map<Int, List<Int>>): List<Int>? {
        if (ordered.size < 2) return null
        if (immediateBefore.values.any { it.size > 1 }) return null
        val after = immediateBefore.entries.flatMap { (waiter, before) -> before.map { it to waiter } }
            .groupBy({ it.first }, { it.second })
        if (after.values.any { it.size > 1 }) return null
        val roots = ordered.filter { immediateBefore[it].isNullOrEmpty() }
        if (roots.size != 1) return null
        val line = generateSequence(roots.single()) { after[it]?.singleOrNull() }.toList()
        return line.takeIf { it.size == ordered.size }
    }

    private data class Supply(val names: List<String>, val items: Long, val singleItem: String?)

    /**
     * What [farm] feeds, for the SUPPLIES and ITEMS SUPPLIED columns.
     *
     * The final projects it feeds, in panel order — that is what the table is about. A farm that
     * feeds none of them directly is in the set because it feeds another farm still to build, and
     * says so, rather than reading "nothing".
     */
    private fun suppliesOf(roadmap: Roadmap, farm: RoadmapNode, terminalIds: List<Int>, farmIds: Set<Int>): Supply {
        val out = roadmap.edges.filter { it.toNodeId == farm.projectId }
        val toFinal = out.filter { it.fromNodeId in terminalIds }
        val lines = toFinal.ifEmpty { out.filter { it.fromNodeId in farmIds } }
        val names = if (toFinal.isNotEmpty()) {
            terminalIds.mapNotNull { id -> toFinal.firstOrNull { it.fromNodeId == id }?.fromNodeName }
        } else {
            lines.map { it.fromNodeName }.distinct().sortedBy { it.lowercase() }
        }
        val items = lines.mapNotNull { it.itemName }.distinct()
        return Supply(
            names = names,
            items = lines.sumOf { it.quantity ?: 0L },
            singleItem = items.singleOrNull(),
        )
    }

    /**
     * Building first, then paused, then planned: finishing what is open needs no prices. "Building"
     * is a started project, not an ACTIVE one — see [Row.started].
     */
    internal fun progressRank(state: ProjectState, started: Boolean): Int = when {
        state == ProjectState.PAUSED -> 1
        started -> 0
        else -> 2
    }

    /** "◐ building", "‖ paused", "○ planned" — a glyph beside every word, never colour alone. */
    fun progressLabel(state: ProjectState, started: Boolean): String = when {
        state == ProjectState.PAUSED -> "‖ paused"
        started -> "◐ building"
        else -> "○ planned"
    }

    /**
     * A final project's state as a short label, for the list of those past the panels: building or
     * planned by [progressLabel]'s rule, like every farm, and any other state by its name.
     */
    fun stateLabel(state: ProjectState, started: Boolean): String = when (state) {
        ProjectState.ACTIVE, ProjectState.PENDING, ProjectState.PAUSED -> progressLabel(state, started)
        else -> state.name.lowercase()
    }

    /** "Storage System YAMS only", "Storage System YAMS + Copper Library". */
    fun suppliesText(names: List<String>): String = when (names.size) {
        0 -> "—"
        1 -> "${names.single()} only"
        else -> names.joinToString(" + ")
    }

    /** "51,092", or "85 Prismarine Shard" when the farm sends one material. */
    fun itemsText(row: Row): String =
        row.singleItem?.let { "${format(row.items)} ${shortItemName(it)}" } ?: format(row.items)

    /** "waits on Cobble farm for 3,787 Cobblestone" — one clause per farm it waits on. */
    fun waitsText(waits: List<Wait>): String = "waits on " + waits.joinToString("; ") { wait ->
        val amount = wait.quantity?.let { "${format(it)} " } ?: ""
        wait.itemName?.let { "${wait.name} for $amount${shortItemName(it)}" } ?: wait.name
    }

    // ---- grouping by destination (frame 5A) ------------------------------------------------

    /** One run of the TO BUILD table, named for where its farms' output goes. */
    data class Group(val supplies: List<String>, val rows: List<Row>)

    /**
     * The table grouped by what each farm feeds, or null when state still separates the rows.
     *
     * A fresh world's farms are all planned, so "state, then name" collapses into "name" and the
     * table stops saying anything. Grouping by destination puts the one fact that does differ up
     * front — and uses the supply column's groups, so a farm that goes DONE lands in the group it
     * was already listed under.
     *
     * Only when nothing is ordered either: a chained farm sits under the farm it waits on (4C), and
     * a group heading would cut it away from its parent.
     *
     * Ordered as 5A draws it: the farms feeding the most final projects first, then one group per
     * final project in panel order, then farms that feed only other farms. [emptyFinals] are the
     * drawn final projects no farm feeds alone — stated, not omitted, as the supply column does.
     */
    fun groupsOf(toBuild: ToBuild, drawn: List<RoadmapNode>): Grouped? {
        if (toBuild.rows.isEmpty() || toBuild.ordered.isNotEmpty()) return null
        if (toBuild.rows.map { it.label }.distinct().size > 1) return null

        val panelOrder = drawn.map { it.projectName }
        val groups = toBuild.rows
            .groupBy { it.supplies }
            .map { (supplies, rows) -> Group(supplies, rows.sortedBy { it.name.lowercase() }) }
            .sortedWith(
                compareByDescending<Group> { it.supplies.size }
                    .thenBy { group -> group.supplies.firstOrNull()?.let { panelOrder.indexOf(it) }?.takeIf { it >= 0 } ?: Int.MAX_VALUE }
                    .thenBy { it.supplies.joinToString() }
            )
        val alone = groups.filter { it.supplies.size == 1 }.mapTo(mutableSetOf()) { it.supplies.single() }
        return Grouped(
            // "planned", "building", "paused" — the label without its glyph, for "ALL PLANNED".
            stateWord = toBuild.rows.first().label.substringAfter(' '),
            groups = groups,
            emptyFinals = panelOrder.filter { it !in alone },
        )
    }

    data class Grouped(val stateWord: String, val groups: List<Group>, val emptyFinals: List<String>)

    // ---- copy -----------------------------------------------------------------------------

    /** "six" for counts a reader takes in at a glance, digits past twelve. */
    fun countWord(n: Int): String = listOf(
        "none", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
    ).getOrElse(n) { n.toString() }

    /** "◐ Building", "◐ Both building", "◐ All three building". */
    fun finishBadge(inProgress: Int): String = "◐ " + when (inProgress) {
        1 -> "Building"
        2 -> "Both building"
        else -> "All ${countWord(inProgress)} building"
    }

    /**
     * The paragraphs under FINISH THESE FIRST — or, with nothing started, under the section that
     * stands in for it.
     *
     * The recommendation is the one that needs no cost model: finish what is open before starting
     * another. Which farm saves the most is a question about prices, and Seam's are only relatively
     * calibrated (MCO-564), so the copy says that rather than ranking anyway.
     */
    fun finishNotes(toBuild: ToBuild): List<String> {
        val total = toBuild.rows.size
        val active = toBuild.inProgress.size
        val of = countWord(total)
        val prices = "Which of the $of takes the most off your hands is a question about prices, and the " +
            "prices are not calibrated yet"
        val lead = when {
            active == 0 && total == 1 -> "The one farm left to build is not started yet."
            active == 0 -> "${if (total == 2) "Neither of the two" else "None of the $of"} is started yet. $prices, " +
                "so the table does not rank them — any of them is a fine place to start."
            active == total && total == 1 -> "The only farm left to build is already in progress."
            active == total -> "All $of farms left to build are already in progress. $prices — but finishing " +
                "what is open before starting another needs no prices at all."
            else -> {
                val rest = toBuild.rows - toBuild.inProgress.toSet()
                val what = when (rest.map { it.label }.toSet()) {
                    setOf(progressLabel(ProjectState.PENDING, started = false)) -> "planned"
                    setOf(progressLabel(ProjectState.PAUSED, started = false)) -> "paused"
                    else -> "planned or paused"
                }
                "${countWord(active).replaceFirstChar { it.uppercase() }} of the $of " +
                    "${if (active == 1) "is" else "are"} already in progress. $prices — but finishing what is " +
                    "open before starting another needs no prices at all. The other ${countWord(rest.size)} " +
                    "${if (rest.size == 1) "is" else "are"} $what."
            }
        }

        val waiting = toBuild.rows.count { it.waitsOn.isNotEmpty() }
        val order = when {
            toBuild.unsettled > 0 && waiting == 0 -> {
                val who = if (toBuild.unsettled == total && total == 2) {
                    "The two farms each need the other"
                } else {
                    "${countWord(toBuild.unsettled).replaceFirstChar { it.uppercase() }} of them need each other"
                }
                "$who, and which comes first is the question above — until it is answered, nothing here has an order."
            }
            waiting > 0 -> {
                val rest = if (toBuild.ordered.size < total) ", and nothing orders the rest" else ""
                val where = if (toBuild.chain != null) "the list below numbers that order" else "the band below draws that order"
                "${countWord(waiting).replaceFirstChar { it.uppercase() }} of them " +
                    "${if (waiting == 1) "waits" else "wait"} on another farm still to build; $where$rest."
            }
            total == 1 -> "It waits on nothing, so this is not a dependency — only where to spend the next session."
            else -> "None of them waits on another, so none of this is a dependency — it is only where to " +
                "spend the next session."
        }
        return listOf(lead, order)
    }

    private fun chainsOf(nodes: Set<Int>, pairs: List<Pair<Int, Int>>): Int {
        val parent = nodes.associateWith { it }.toMutableMap()
        fun find(x: Int): Int {
            var root = x
            while (parent.getValue(root) != root) root = parent.getValue(root)
            return root
        }
        pairs.forEach { (a, b) -> parent[find(a)] = find(b) }
        return nodes.map { find(it) }.toSet().size
    }

    // ---- the band (4C, 5B) -----------------------------------------------------------------

    private data class Band(val graph: RoadmapGraphLayout.Graph, val hidden: Int)

    /**
     * A partial order, laid out by how many farms stand before each.
     *
     * Columns, not one row: a partial order can fork, and two farms waiting on the same one are
     * not sequenced relative to each other — putting them side by side in a row would draw the
     * very order the band exists to withhold. Past [MAX_BAND_COLUMNS] the depth wraps to another
     * row of columns (5B) rather than being cut off. Null when nothing has order.
     *
     * Arrows and sub-lines follow [immediateBefore] only. Every wait is still in the table; drawn,
     * the ones a longer path already implies ran behind the nodes between, with their labels landing
     * between farms they had nothing to do with.
     */
    private fun bandOf(
        ordered: List<RoadmapNode>,
        waitsOn: Map<Int, List<Wait>>,
        immediateBefore: Map<Int, List<Int>>,
        farmCount: Int,
        sortKey: Comparator<RoadmapNode>,
        started: Set<Int>,
    ): Band? {
        if (ordered.isEmpty()) return null
        val ids = ordered.mapTo(mutableSetOf()) { it.projectId }
        val names = ordered.associate { it.projectId to it.projectName }
        val before = ordered.associate { farm ->
            farm.projectId to immediateBefore[farm.projectId].orEmpty().filter { it in ids }
        }

        val depth = depthsOf(ids, before)

        val depths = (depth.values.maxOrNull() ?: 0) + 1
        val columns = depths.coerceAtMost(MAX_BAND_COLUMNS)
        val gap = if (columns < MAX_BAND_COLUMNS) BAND_GAP else BAND_GAP_TIGHT
        val width = if (columns < MAX_BAND_COLUMNS) {
            BAND_NODE_WIDTH
        } else {
            (BAND_WIDTH - 2 * BAND_LEFT - (columns - 1) * gap) / columns
        }

        // Rows within a depth follow the farms they wait on, so a fork does not cross itself.
        val rowOf = mutableMapOf<Int, Int>()
        (0 until depths.coerceAtMost(MAX_BAND_COLUMNS * MAX_BAND_BLOCKS)).forEach { level ->
            ordered
                .filter { depth[it.projectId] == level }
                .sortedWith(
                    compareBy<RoadmapNode> { farm ->
                        before.getValue(farm.projectId).mapNotNull { rowOf[it] }.minOrNull() ?: -1
                    }.then(sortKey)
                )
                .take(MAX_BAND_ROWS)
                .forEachIndexed { row, farm -> rowOf[farm.projectId] = row }
        }
        val rows = (rowOf.values.maxOrNull() ?: 0) + 1
        val blockStride = rows * BAND_PITCH + BAND_BLOCK_GAP

        val nodes = ordered.filter { it.projectId in rowOf }.map { farm ->
            val id = farm.projectId
            val waits = before.getValue(id).mapNotNull { names[it] }.sortedBy { it.lowercase() }
            val tail = when (waits.size) {
                0 -> "nothing before it"
                1 -> "waits on ${waits.single()}"
                else -> "waits on ${waits.first()} + ${waits.size - 1} more"
            }
            val level = depth.getValue(id)
            GraphNode(
                key = "order-$id",
                // The accent border marks where a chain starts.
                kind = if (waits.isEmpty()) NodeKind.START else NodeKind.SEQUENCE,
                projectId = id,
                title = farm.projectName,
                subLines = listOf(SubLine("${progressLabel(farm.state, id in started)} · $tail")),
                x = BAND_LEFT + (level % MAX_BAND_COLUMNS) * (width + gap),
                y = BAND_TOP + (level / MAX_BAND_COLUMNS) * blockStride + rowOf.getValue(id) * BAND_PITCH,
                width = width,
                height = BAND_NODE_HEIGHT,
            )
        }
        val placed = nodes.associateBy { it.projectId!! }

        fun laneBelow(block: Int) =
            BAND_TOP + block * blockStride + (rows - 1) * BAND_PITCH + BAND_NODE_HEIGHT + BAND_BLOCK_GAP / 2
        var lowestLane = 0
        val edges = nodes.flatMap { to ->
            before.getValue(to.projectId!!).mapNotNull { fromId ->
                val from = placed[fromId] ?: return@mapNotNull null
                val wait = waitsOn[to.projectId].orEmpty().firstOrNull { it.projectId == fromId }
                val x1 = from.x + from.width
                val y1 = from.y + from.height / 2
                val x2 = to.x - 2
                val y2 = to.y + to.height / 2
                val fromLevel = depth.getValue(fromId)
                val toLevel = depth.getValue(to.projectId)
                val adjacent = fromLevel / MAX_BAND_COLUMNS == toLevel / MAX_BAND_COLUMNS && toLevel == fromLevel + 1
                val path = when {
                    // Anything but the next column of the same block — a wait that skips a column,
                    // or crosses into the next row of columns — would cut through the farms in
                    // between. It runs only where no farm is: out into the column gap beside the
                    // source, along the gap under the source's block, and back along the column gap
                    // beside the target.
                    !adjacent -> {
                        val xr = x1 + minOf(gap / 2, (BAND_WIDTH - x1) / 2)
                        val xl = to.x - minOf(gap / 2, to.x / 2)
                        val lane = laneBelow(fromLevel / MAX_BAND_COLUMNS).also { lowestLane = maxOf(lowestLane, it) }
                        "M $x1 $y1 L $xr $y1 L $xr $lane L $xl $lane L $xl $y2 L $x2 $y2"
                    }
                    y1 == y2 -> "M $x1 $y1 L $x2 $y2"
                    else -> {
                        val bend = (x2 - x1) / 2
                        "M $x1 $y1 C ${x1 + bend} $y1 ${x2 - bend} $y2 $x2 $y2"
                    }
                }
                GraphEdge(
                    key = "order-$fromId-${to.projectId}",
                    path = path,
                    // Lighter than the supply graph's ropes: the band's arrows say "before", not "how
                    // much", and at full weight a 3,787-item hop carried an arrowhead half a node tall.
                    strokeWidth = RoadmapGraphLayout.strokeWidthFor(wait?.quantity).coerceAtMost(MAX_BAND_STROKE),
                    // A hand-made ordering carries no material — the legend's ┄, as everywhere else.
                    dashed = wait?.itemName == null,
                    label = wait?.itemName?.takeIf { adjacent && y1 == y2 && gap == BAND_GAP }?.let { item ->
                        val amount = wait.quantity?.let { "${format(it)} " } ?: ""
                        RoadmapGraphLayout.EdgeLabel("$amount${shortItemName(item)}", (x1 + x2) / 2, y1 - 10, "middle")
                    },
                )
            }
        }

        // To the lowest farm drawn — a wrapped block can hold fewer rows than the first — or the
        // lowest lane an edge runs along, whichever is further down.
        val height = maxOf(nodes.maxOf { it.y + it.height } + BAND_BOTTOM_PADDING, lowestLane + BAND_BOTTOM_PADDING / 2)
        val right = nodes.filter { it.y < BAND_TOP + blockStride }.maxOf { it.x + it.width }
        val noteInside = BAND_WIDTH - right - 28 - BAND_LEFT >= BAND_NOTE_MIN_WIDTH

        val graph = RoadmapGraphLayout.Graph(
            nodes = nodes,
            edges = RoadmapGraphLayout.dropCollidingLabels(edges, nodes),
            groups = emptyList(),
            rails = emptyList(),
            width = BAND_WIDTH,
            height = height,
            bandCaption = RoadmapGraphLayout.BandCaption(
                "SEQUENCE · ${ordered.size} OF THE ${farmCount} FARMS",
                x = BAND_LEFT,
                y = 10,
            ),
            bundled = false,
            note = if (noteInside) {
                RoadmapGraphLayout.BandNote(x = right + 28, y = BAND_TOP + 6, width = BAND_WIDTH - right - 28 - BAND_LEFT)
            } else {
                null
            },
        )
        return Band(graph, hidden = ordered.size - nodes.size)
    }
}
