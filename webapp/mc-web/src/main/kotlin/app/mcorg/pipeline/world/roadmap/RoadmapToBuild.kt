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

    // ---- band geometry, on the band panel's own 1032px coordinate system ----------------

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
    private const val MAX_BAND_STROKE = 2.2

    /** Room the in-band note needs beside the last column before it moves below the panel. */
    private const val BAND_NOTE_MIN_WIDTH = 260

    /**
     * The band is bounded like every other strip on the page. A chain deeper than this, or a
     * column taller, still lists every farm in the table; only the drawing stops.
     */
    const val MAX_BAND_COLUMNS = 4
    const val MAX_BAND_ROWS = 4

    // ---- outputs -------------------------------------------------------------------------

    /** One settled thing a farm waits on: another farm still to build. */
    data class Wait(val projectId: Int, val name: String, val itemName: String?, val quantity: Long?)

    data class Row(
        val projectId: Int,
        val name: String,
        val state: ProjectState,
        /** What it feeds: the final projects, in panel order, or the farms when it feeds none. */
        val supplies: List<String>,
        /** Sum of the supply lines to [supplies]. Mixes crafted and raw items — see the table's note. */
        val items: Long,
        /** The one material, when the farm sends exactly one — "85 Prismarine Shard" says more than "85". */
        val singleItem: String?,
        /** Indentation: 0 for a farm that waits on nothing still to build. */
        val depth: Int,
        val waitsOn: List<Wait>,
        /** In a loop nobody has answered yet, so it has no order to show. */
        val unsettled: Boolean,
    )

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
        val band: RoadmapGraphLayout.Graph?,
        /** Ordered farms the band had no room for; the table still lists them. */
        val bandHidden: Int,
        /** How many of [rows] feed each final project directly, by project id — "16 of the 22 feed it". */
        val feedersByTerminal: Map<Int, Int> = emptyMap(),
    ) {
        val inProgress: List<Row> get() = rows.filter { it.state == ProjectState.ACTIVE }
        val unsettled: Int get() = rows.count { it.unsettled }
        val totalItems: Long get() = rows.sumOf { it.items }
        val isEmpty: Boolean get() = rows.isEmpty()
    }

    // ---- the derivation ------------------------------------------------------------------

    /**
     * Every unfinished project upstream of [terminals], as the TO BUILD table, plus the band for
     * whatever of it has order.
     *
     * Upstream of *any* final project rather than only the drawn ones, which is what the sequence
     * band it replaces covered: a farm feeding the fourth final project is still work, and would
     * otherwise appear in no section of the page.
     */
    fun of(roadmap: Roadmap, terminals: List<RoadmapNode>): ToBuild {
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

        val sortKey = compareBy<RoadmapNode>({ stateRank(it.state) }, { it.projectName.lowercase() }, { it.projectId })

        // --- the table: state then name, a chained farm under the first farm it waits on ---------
        val parentOf = farms.associate { farm ->
            farm.projectId to waitsOn[farm.projectId].orEmpty()
                .mapNotNull { byId[it.projectId] }
                .minWithOrNull(sortKey)
                ?.projectId
        }
        val childrenOf = farms
            .filter { parentOf[it.projectId] != null }
            .groupBy { parentOf.getValue(it.projectId)!! }
            .mapValues { (_, children) -> children.sortedWith(sortKey) }

        val supplies = farms.associate { it.projectId to suppliesOf(roadmap, it, terminalIds, farmIds) }
        val rows = mutableListOf<Row>()
        val placed = mutableSetOf<Int>()
        fun place(farm: RoadmapNode, depth: Int) {
            if (!placed.add(farm.projectId)) return
            val supply = supplies.getValue(farm.projectId)
            rows += Row(
                projectId = farm.projectId,
                name = farm.projectName,
                state = farm.state,
                supplies = supply.names,
                items = supply.items,
                singleItem = supply.singleItem,
                depth = depth,
                waitsOn = waitsOn[farm.projectId].orEmpty().sortedBy { it.name.lowercase() },
                unsettled = farm.projectId in unsettledIds,
            )
            childrenOf[farm.projectId].orEmpty().forEach { place(it, depth + 1) }
        }
        farms.filter { parentOf[it.projectId] == null }.sortedWith(sortKey).forEach { place(it, 0) }
        // A floor, never the path: the edges are acyclic once loops are broken, so every farm has
        // a root above it. Should one ever survive, it is listed flat rather than lost.
        farms.filter { it.projectId !in placed }.sortedWith(sortKey).forEach { place(it, 0) }

        // --- promised supply, per final project ---------------------------------------------
        val promised = roadmap.edges
            .filter { it.toNodeId in farmIds && it.fromNodeId in terminalIds }
            .groupBy { it.fromNodeId }
            .mapValues { (_, edges) -> edges.sumOf { it.quantity ?: 0L } }
            .filterValues { it > 0 }

        val band = bandOf(farms.filter { it.projectId in ordered }, waitsOn, farms.size, sortKey)

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
        )
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

    /** Building first, then paused, then planned: finishing what is open needs no prices. */
    internal fun stateRank(state: ProjectState): Int = when (state) {
        ProjectState.ACTIVE -> 0
        ProjectState.PAUSED -> 1
        ProjectState.PENDING -> 2
        else -> 3
    }

    /** "◐ building", "‖ paused", "○ planned" — a glyph beside every word, never colour alone. */
    fun stateLabel(state: ProjectState): String = when (state) {
        ProjectState.ACTIVE -> "◐ building"
        ProjectState.PAUSED -> "‖ paused"
        ProjectState.PENDING -> "○ planned"
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
        if (toBuild.rows.map { it.state }.distinct().size > 1) return null

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
            state = toBuild.rows.first().state,
            groups = groups,
            emptyFinals = panelOrder.filter { it !in alone },
        )
    }

    data class Grouped(val state: ProjectState, val groups: List<Group>, val emptyFinals: List<String>)

    /** "planned", "building", "paused" — the word without its glyph, for "ALL PLANNED". */
    fun stateWord(state: ProjectState): String = stateLabel(state).substringAfter(' ')

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
                val rest = toBuild.rows.filter { it.state != ProjectState.ACTIVE }
                val states = rest.map { it.state }.toSet()
                val what = when (states) {
                    setOf(ProjectState.PENDING) -> "planned"
                    setOf(ProjectState.PAUSED) -> "paused"
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
                "${countWord(waiting).replaceFirstChar { it.uppercase() }} of them " +
                    "${if (waiting == 1) "waits" else "wait"} on another farm still to build; the band below " +
                    "draws that order$rest."
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

    // ---- the band (4C) ---------------------------------------------------------------------

    private data class Band(val graph: RoadmapGraphLayout.Graph, val hidden: Int)

    /**
     * The ordered farms, laid out by how many farms stand before each.
     *
     * Columns, not one row: a partial order can fork, and two farms waiting on the same one are
     * not sequenced relative to each other — putting them side by side in a row would draw the
     * very order the band exists to withhold. Null when nothing has order.
     */
    private fun bandOf(
        ordered: List<RoadmapNode>,
        waitsOn: Map<Int, List<Wait>>,
        farmCount: Int,
        sortKey: Comparator<RoadmapNode>,
    ): Band? {
        if (ordered.isEmpty()) return null
        val ids = ordered.mapTo(mutableSetOf()) { it.projectId }

        // Longest path from a farm that waits on nothing — Kahn's order over an acyclic graph.
        val before = ordered.associate { farm ->
            farm.projectId to waitsOn[farm.projectId].orEmpty().map { it.projectId }.filter { it in ids }
        }
        val depth = mutableMapOf<Int, Int>()
        var pending = ordered.map { it.projectId }
        while (pending.isNotEmpty()) {
            val ready = pending.filter { id -> before.getValue(id).all { it in depth } }
            if (ready.isEmpty()) break
            ready.forEach { id -> depth[id] = (before.getValue(id).maxOfOrNull { depth.getValue(it) + 1 } ?: 0) }
            pending = pending - ready.toSet()
        }
        pending.forEach { depth[it] = 0 }

        val columns = (depth.values.maxOrNull() ?: 0) + 1
        val drawnColumns = columns.coerceAtMost(MAX_BAND_COLUMNS)
        val gap = if (drawnColumns < MAX_BAND_COLUMNS) BAND_GAP else BAND_GAP_TIGHT
        val width = if (drawnColumns < MAX_BAND_COLUMNS) {
            BAND_NODE_WIDTH
        } else {
            (RoadmapGraphLayout.PANEL_WIDTH - 2 * BAND_LEFT - (drawnColumns - 1) * gap) / drawnColumns
        }

        // Rows within a column follow the farms they wait on, so a fork does not cross itself.
        val rowOf = mutableMapOf<Int, Int>()
        (0 until drawnColumns).forEach { column ->
            ordered
                .filter { depth[it.projectId] == column }
                .sortedWith(
                    compareBy<RoadmapNode> { farm ->
                        before.getValue(farm.projectId).mapNotNull { rowOf[it] }.minOrNull() ?: -1
                    }.then(sortKey)
                )
                .take(MAX_BAND_ROWS)
                .forEachIndexed { row, farm -> rowOf[farm.projectId] = row }
        }

        val nodes = ordered.filter { it.projectId in rowOf }.map { farm ->
            val id = farm.projectId
            val waits = waitsOn[id].orEmpty().filter { it.projectId in ids }.sortedBy { it.name.lowercase() }
            val tail = when (waits.size) {
                0 -> "nothing before it"
                1 -> "waits on ${waits.single().name}"
                else -> "waits on ${waits.first().name} + ${waits.size - 1} more"
            }
            GraphNode(
                key = "order-$id",
                // The accent border marks where a chain starts, as START does in the old band.
                kind = if (waits.isEmpty()) NodeKind.START else NodeKind.SEQUENCE,
                projectId = id,
                title = farm.projectName,
                subLines = listOf(SubLine("${stateLabel(farm.state)} · $tail")),
                x = BAND_LEFT + depth.getValue(id) * (width + gap),
                y = BAND_TOP + rowOf.getValue(id) * BAND_PITCH,
                width = width,
                height = BAND_NODE_HEIGHT,
            )
        }
        val placed = nodes.associateBy { it.projectId!! }

        val edges = nodes.flatMap { to ->
            waitsOn[to.projectId!!].orEmpty().mapNotNull { wait ->
                val from = placed[wait.projectId] ?: return@mapNotNull null
                val x1 = from.x + from.width
                val y1 = from.y + from.height / 2
                val x2 = to.x - 2
                val y2 = to.y + to.height / 2
                val path = if (y1 == y2) {
                    "M $x1 $y1 L $x2 $y2"
                } else {
                    val bend = (x2 - x1) / 2
                    "M $x1 $y1 C ${x1 + bend} $y1 ${x2 - bend} $y2 $x2 $y2"
                }
                GraphEdge(
                    key = "order-${wait.projectId}-${to.projectId}",
                    path = path,
                    // Lighter than the supply graph's ropes: the band's arrows say "before", not "how
                    // much", and at full weight a 3,787-item hop carried an arrowhead half a node tall.
                    strokeWidth = RoadmapGraphLayout.strokeWidthFor(wait.quantity).coerceAtMost(MAX_BAND_STROKE),
                    // A hand-made ordering carries no material — the legend's ┄, as everywhere else.
                    dashed = wait.itemName == null,
                    label = wait.itemName?.let { item ->
                        val amount = wait.quantity?.let { "${format(it)} " } ?: ""
                        RoadmapGraphLayout.EdgeLabel("$amount${shortItemName(item)}", (x1 + x2) / 2, minOf(y1, y2) - 10, "middle")
                    },
                )
            }
        }

        val rows = (rowOf.values.maxOrNull() ?: 0) + 1
        val height = BAND_TOP + rows * BAND_PITCH - (BAND_PITCH - BAND_NODE_HEIGHT) + BAND_BOTTOM_PADDING
        val right = nodes.maxOf { it.x + it.width }
        val noteInside = RoadmapGraphLayout.PANEL_WIDTH - right - 28 - BAND_LEFT >= BAND_NOTE_MIN_WIDTH

        val graph = RoadmapGraphLayout.Graph(
            nodes = nodes,
            edges = RoadmapGraphLayout.dropCollidingLabels(edges, nodes),
            groups = emptyList(),
            rails = emptyList(),
            width = RoadmapGraphLayout.PANEL_WIDTH,
            height = height,
            bandCaption = RoadmapGraphLayout.BandCaption(
                "SEQUENCE · ${ordered.size} OF THE ${farmCount} FARMS",
                x = BAND_LEFT,
                y = 10,
            ),
            bundled = false,
            note = if (noteInside) {
                RoadmapGraphLayout.BandNote(x = right + 28, y = BAND_TOP + 6, width = RoadmapGraphLayout.PANEL_WIDTH - right - 28 - BAND_LEFT)
            } else {
                null
            },
        )
        return Band(graph, hidden = ordered.size - nodes.size)
    }
}
