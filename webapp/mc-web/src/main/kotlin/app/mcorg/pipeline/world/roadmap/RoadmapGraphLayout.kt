package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapNode
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The world roadmap as a weighted dependency graph (MCO-469).
 *
 * Pure geometry: takes the derived [Roadmap] plus the two numbers the roadmap query does not
 * carry ([handGathered], [terminalStats]) and returns node rectangles, edge paths and a panel
 * height. No database, no HTML — so the whole layout is unit-testable, which matters because
 * the design's correctness lives almost entirely in the numbers below.
 *
 * ## The idea the geometry encodes
 *
 * The graph is **supply, not sequence**: a column of finished farms on the left, grouped by the
 * final project they feed, ropes into an intake rail beside each project's panel on the right.
 * Nothing unbuilt ever enters the column — nothing there is waiting on anything.
 *
 * The farms still to build are not in the graph at all. They are the TO BUILD table, and the
 * order band above it for whatever order really exists among them ([RoadmapToBuild], MCO-571);
 * the graph keeps only a [Promised] tab with one dashed rope per panel they feed.
 *
 * *(It used to carry a sequence band across its top as well — 2A's design, unbuilt projects left
 * to right in build order. It sequenced sets that had no order, so 4B moved the work into the
 * table, and 5A gave the nothing-built world panels with no column rather than the band: no world
 * draws it any more (MCO-544).)*
 *
 * A world where no farm produces yet has no graph from this object at all: with no column there is
 * nothing to lay out, and the page draws the panels as a plain row instead (frame 5A).
 *
 * ## Why the producer column is capped
 *
 * Forever world lands 86 edges on one node while every other row has at most two. A layout that
 * scales with edge count fails on exactly the row that matters, so the column keeps the
 * [TOP_PRODUCERS] largest producers and collapses the rest into one bundle — the panel is
 * O(1) in producers, not O(n).
 */
object RoadmapGraphLayout {

    // ---- geometry, all in CSS px on the panel's own coordinate system -------------------

    /** 1080px card minus 2×24px padding. The panel never scrolls horizontally. */
    const val PANEL_WIDTH = 1032

    const val COLUMN_WIDTH = 216
    const val NODE_HEIGHT = 56

    /** 56px node + 6px gutter. The design's "vertical rhythm". */
    const val PITCH = 62

    /** The hand-gathered node carries a third line, so it is taller than a producer. */
    const val HAND_HEIGHT = 80

    const val TERMINAL_LEFT = 796
    const val TERMINAL_WIDTH = 236

    /**
     * A final project's panel height. A single panel grows with its content, as 2A draws it, and
     * this is only its nominal height for label collisions. Stacked panels (MCO-563) are placed
     * one below the other, so each gets exactly this height.
     */
    const val TERMINAL_HEIGHT = 210

    /** One row of a panel's 11px facts list plus its gap, measured on the rendered page. */
    private const val SPLIT_ROW_HEIGHT = 18

    /**
     * Where the panels start: clear of the promised-supply tab and the short rope out of it into
     * the top panel (MCO-571).
     */
    const val TERMINAL_TOP = 50
    private const val PROMISED_LEFT = 560
    private const val PROMISED_TOP = 4
    private const val PROMISED_HEIGHT = 26

    /** Right of the panels, where the ropes into the lower panels run down past the upper ones. */
    private const val PROMISED_TRUNK_STRIP = 16
    const val TERMINAL_GAP = 12

    /**
     * Final projects drawn as panels. The right column is a fixed strip, bounded for the same
     * reason as the band and the producer column: any past this are counted in one node.
     */
    const val MAX_TERMINALS = 3
    private const val TERMINAL_MORE_HEIGHT = 72

    /** Producers kept as their own node; everything past this collapses into the bundle. */
    const val TOP_PRODUCERS = 5

    /**
     * Producers drawn per destination group, before the group collapses the rest into a bundle.
     *
     * Lower than [TOP_PRODUCERS] because the column now holds a group per final project rather
     * than one list: at three panels, five each would be a column of sixteen nodes against a
     * right-hand side of three.
     */
    const val TOP_PER_GROUP = 3

    private const val GROUP_HEADER_OFFSET = 16
    private const val PANEL_BOTTOM_PADDING = 20
    private const val CAPTION_GAP = 10
    private const val CAPTION_HEIGHT = 14

    /** Gap between column nodes; [PITCH] is this plus [NODE_HEIGHT]. */
    private const val GUTTER = 6

    /** Between a group's rule and its first node. */
    private const val GROUP_GAP = 14

    /**
     * One 11px sub-line plus its 2px gap, for nodes that carry more than one.
     *
     * Measured against the rendered page rather than the font size: at 14 the by-hand node's last
     * split line sat outside its own dashed border.
     */
    private const val SUB_LINE_HEIGHT = 18

    /** Breathing room after a run that drew nothing, so two "· none" headers do not stack. */
    private const val EMPTY_GROUP_GAP = 10

    /**
     * The intake rail sits just left of the panel, and every rope lands on it — see [IntakeRail].
     * [RAIL_INSET] keeps the first and last dot clear of the panel's own corners.
     */
    private const val RAIL_X = TERMINAL_LEFT - 10
    private const val RAIL_INSET = 28

    private const val MIN_STROKE = 1.0
    private const val MAX_STROKE = 4.5

    // ---- inputs ------------------------------------------------------------------------

    /**
     * One finished farm's contribution, rolled up across every item it supplies.
     *
     * [items] is the sum the design draws its stroke weight from; [edges] is how many distinct
     * materials run along the pair, which is what makes End Trading Hall interesting (1,625
     * items across 39 edges — a hairline carrying a lot of rows).
     */
    data class Producer(
        val projectId: Int,
        val name: String,
        val items: Long,
        val edges: Int,
        /** The single largest material, for the node's sub-line. Null when nothing is named. */
        val largestItemName: String? = null,
        val largestItemQuantity: Long? = null,
        /**
         * What this producer sends to each final project, by project id (MCO-563). Empty means
         * no split was recorded, and the producer feeds whatever it is drawn against.
         */
        val itemsByTerminal: Map<Int, Long> = emptyMap(),
    )

    /** Raw material nobody's farm covers — a synthetic node, never a project. */
    data class HandGathered(
        val items: Long,
        val materials: Int,
        /** "Oak Log + Ice = 84%" — the concentration warning, or null when there is no tail. */
        val concentration: String? = null,
        /** What is left to gather by hand for each final project, as on [Producer.itemsByTerminal]. */
        val itemsByTerminal: Map<Int, Long> = emptyMap(),
        /**
         * How much of [items] the farms still to build would take off the list (MCO-572) — the drawn
         * panels' `promised`, summed. Zero when nothing is promised or it could not be measured.
         */
        val promised: Long = 0,
    )

    /** The numbers on the terminal panel, which come from the plan rather than the graph. */
    data class TerminalStats(
        val fromFarms: Long,
        val byHand: Long,
        val craftRows: Int,
        val openQuestions: Int,
        val percentComplete: Int,
        /** How many finished farms feed this panel — the count beside the items, "from 16 farms". */
        val farms: Int = 0,
        /** [byHand] split into promised and yours either way (MCO-572); null when it could not be measured. */
        val split: HandListSplit.Split? = null,
    )

    // ---- outputs -----------------------------------------------------------------------

    enum class Tone { DEFAULT, MUTED, GREEN, RED, AMBER, ACCENT, DISABLED }

    enum class NodeKind { SEQUENCE, START, SUPPLY, BUNDLE, HAND, TERMINAL, PROMISED }

    data class SubLine(val text: String, val tone: Tone = Tone.MUTED)

    data class GraphNode(
        val key: String,
        val kind: NodeKind,
        /** Null for the two synthetic nodes (bundle, by-hand) — they link nowhere. */
        val projectId: Int?,
        val title: String,
        val subLines: List<SubLine>,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        /** Small tracked label above the title, e.g. "START HERE" or "FINAL PROJECT · 1 OF 3". */
        val eyebrow: String? = null,
        val eyebrowTone: Tone = Tone.MUTED,
        /** Which [GroupHeader] this node belongs under, for the template's document order. */
        val group: String? = null,
    )

    /**
     * A rule + caption introducing a run of nodes in the supply column.
     *
     * [kinds] is what the header introduces. On desktop the header is placed absolutely and
     * document order is irrelevant, but the mobile fallback drops absolute positioning and
     * falls back to *document* order — where a header emitted before every node lands above
     * the wrong group. Carrying the membership lets the template interleave correctly for
     * both, from one list.
     */
    data class GroupHeader(
        val text: String,
        val note: String?,
        val tone: Tone,
        val y: Int,
        val ruleY: Int,
        val dashedRule: Boolean,
        /** Matches [GraphNode.group] — the column now holds a run per final project, and node kind no longer identifies which. */
        val key: String,
    )

    data class EdgeLabel(val text: String, val x: Int, val y: Int, val anchor: String)

    data class GraphEdge(
        val key: String,
        val path: String,
        val strokeWidth: Double,
        val dashed: Boolean,
        val label: EdgeLabel? = null,
        /** False for a rope landing on an [IntakeRail]: the rail carries the one arrowhead. */
        val marker: Boolean = true,
    )

    /**
     * A final project's intake: the ropes feeding it end as dots on this rail, and the rail takes
     * the one arrowhead into the panel.
     *
     * Seven ropes landing on a panel's left edge put seven arrowheads in 150px, which read as a
     * porcupine rather than as supply (MCO-566). The rail is one target for the column: a rope
     * arrives at a dot, and what enters the *project* is a single arrow.
     */
    data class IntakeRail(
        val key: String,
        val x: Int,
        val top: Int,
        val bottom: Int,
        /** "INTAKE · 7 SUPPLIERS", or "NO FARM INTAKE" for a project no farm feeds. */
        val label: String,
        val labelY: Int,
        /** Where each rope lands, top to bottom. Empty when nothing feeds this panel. */
        val dots: List<Int>,
        /** Y of the single arrow from rail into panel; null when there is nothing to carry. */
        val arrowY: Int?,
    )

    data class Graph(
        val nodes: List<GraphNode>,
        val edges: List<GraphEdge>,
        val groups: List<GroupHeader>,
        val rails: List<IntakeRail>,
        val width: Int,
        val height: Int,
        val bandCaption: BandCaption?,
        /** True when the producer column was capped, so the template can offer the expander. */
        val bundled: Boolean,
        /** Where a panel's explanatory note sits, when it fits inside the panel at all. */
        val note: BandNote? = null,
    )

    data class BandCaption(val text: String, val x: Int, val y: Int)

    data class BandNote(val x: Int, val y: Int, val width: Int)

    /**
     * The farms still to build, standing in the graph as one dashed tab (MCO-571, frames 4A/4B).
     *
     * They stay out of the supply column — nothing in it waits on anything — and out of a band
     * that would assert an order among them that does not exist. What the graph still owes the
     * reader is that their supply is coming, and to which panel: one dashed rope per panel, from
     * a tab that links to the TO BUILD table. O(1) whether six farms are in flight or forty.
     */
    data class Promised(
        val farms: Int,
        /** What all of them together send each final project, by project id. */
        val itemsByTerminal: Map<Int, Long>,
    )

    // ---- the algorithm ------------------------------------------------------------------

    /**
     * Lays out [roadmap]'s graph, or returns null when there is no terminal project to draw
     * toward — a world with no edges has no chain, and the page falls back to its list
     * sections rather than drawing an empty panel.
     */
    fun of(
        roadmap: Roadmap,
        producers: List<Producer>,
        handGathered: HandGathered?,
        /** Each project's planned demand, for [terminalsOf] — the same map the page chose its panels by. */
        demand: Map<Int, Long> = emptyMap(),
        /** The farms still to build, drawn as one tab with a rope per panel (MCO-571); null draws no tab. */
        promised: Promised? = null,
        /**
         * Whether any panel carries the promised / yours-either-way rows (MCO-572). Stacked panels
         * have a fixed height, and two more rows would push the last fact out of the box.
         */
        splitRows: Boolean = false,
    ): Graph? {
        val terminalHeight = TERMINAL_HEIGHT + if (splitRows) 2 * SPLIT_ROW_HEIGHT else 0
        val terminals = terminalsOf(roadmap, demand)
        if (terminals.isEmpty()) return null
        val drawnTerminals = terminals.take(MAX_TERMINALS)
        val hiddenTerminals = terminals.size - drawnTerminals.size

        val columnGroups = groupByDestination(producers, drawnTerminals)

        val nodes = mutableListOf<GraphNode>()
        val groups = mutableListOf<GroupHeader>()
        val edges = mutableListOf<GraphEdge>()

        // What each column node sends to each panel, collected while the column is laid out and
        // read when the ropes are drawn: a rope's weight is what *that* node sends to *that*
        // panel, and a node feeding nothing there gets no rope at all.
        val feeders = mutableListOf<Pair<GraphNode, Map<Int, Long>>>()

        // --- supply column, one run per destination ---------------------------------------
        var y = 4
        var bundledAny = false
        columnGroups.forEach { group ->
            val key = group.terminalId?.let { "group-$it" } ?: "group-shared"
            groups += GroupHeader(
                text = group.header,
                note = group.note,
                tone = Tone.GREEN,
                y = y,
                ruleY = y + GROUP_HEADER_OFFSET,
                dashedRule = false,
                key = key,
            )
            y += GROUP_HEADER_OFFSET + GROUP_GAP

            if (group.producers.isEmpty()) y += EMPTY_GROUP_GAP

            val (drawnProducers, bundle) = splitProducers(group.producers, TOP_PER_GROUP)
            drawnProducers.forEach { producer ->
                val lines = producerSubLines(producer, group.terminalId, drawnTerminals)
                val node = GraphNode(
                    key = "supply-${producer.projectId}",
                    kind = NodeKind.SUPPLY,
                    projectId = producer.projectId,
                    title = producer.name,
                    subLines = lines,
                    x = 0, y = y, width = COLUMN_WIDTH, height = nodeHeightFor(lines.size),
                    group = key,
                )
                nodes += node
                feeders += node to producer.itemsByTerminal.orFedAgainst(drawnTerminals, producer.items)
                y += node.height + GUTTER
            }

            bundle?.let {
                bundledAny = true
                val node = GraphNode(
                    key = "supply-bundle-$key",
                    kind = NodeKind.BUNDLE,
                    projectId = null,
                    title = "✓ ${it.count} more, all done",
                    subLines = listOf(
                        SubLine("${format(it.items)} items · ${it.edges} edges ▸", Tone.ACCENT),
                    ),
                    x = 0, y = y, width = COLUMN_WIDTH, height = NODE_HEIGHT,
                    group = key,
                )
                nodes += node
                feeders += node to it.itemsByTerminal.orFedAgainst(drawnTerminals, it.items)
                y += NODE_HEIGHT + GUTTER
            }
        }

        // --- the hand-gathered node, always last and always dashed ------------------------
        // Last, and directly under the run that feeds more than one panel: it feeds every panel,
        // so any other position sends its rope back across the field it just crossed.
        handGathered?.let { hand ->
            val key = "group-hand"
            val headerY = y + 6
            groups += GroupHeader(
                text = "NOT A PROJECT · YOU GATHER IT",
                note = null,
                tone = Tone.MUTED,
                y = headerY,
                ruleY = headerY + GROUP_HEADER_OFFSET,
                dashedRule = true,
                key = key,
            )
            val lines = buildList {
                add(SubLine("${format(hand.items)} items · ${hand.materials} materials"))
                // Frame 4A swaps the concentration warning for this one when farms are being built:
                // "half of it goes away when you finish what you started" is the more actionable
                // fact, and the node has room for one amber line, not two.
                val promisedShare = promisedShareOf(hand)
                when {
                    promisedShare != null -> add(SubLine("⚠ $promisedShare% of it is promised", Tone.AMBER))
                    hand.concentration != null -> add(SubLine("⚠ ${hand.concentration}", Tone.AMBER))
                }
                addAll(splitSubLines(hand.itemsByTerminal, drawnTerminals))
            }
            val handY = headerY + GROUP_HEADER_OFFSET + GROUP_GAP
            val node = GraphNode(
                key = "hand",
                kind = NodeKind.HAND,
                projectId = null,
                title = "By hand",
                subLines = lines,
                x = 0, y = handY, width = COLUMN_WIDTH, height = maxOf(HAND_HEIGHT, nodeHeightFor(lines.size)),
                group = key,
            )
            nodes += node
            feeders += node to hand.itemsByTerminal.orFedAgainst(drawnTerminals, hand.items)
            y = handY + node.height
        }

        val columnBottom = y

        // --- final project panels ------------------------------------------------------------
        // One per project the world drains into, largest demand first (MCO-563). A single panel
        // grows with its content, as 2A draws it; stacked panels need a known height, because
        // each is placed below the last. They sit just under the promised-supply tab, and give up a
        // strip on their right for the ropes into the lower panels to run down.
        val stacked = drawnTerminals.size > 1
        val panelTop = TERMINAL_TOP
        val panelWidth = TERMINAL_WIDTH - PROMISED_TRUNK_STRIP
        val terminalTops = drawnTerminals.withIndex().associate { (index, terminal) ->
            terminal.projectId to panelTop + index * (terminalHeight + TERMINAL_GAP)
        }
        drawnTerminals.forEachIndexed { index, terminal ->
            nodes += GraphNode(
                key = "terminal-${terminal.projectId}",
                kind = NodeKind.TERMINAL,
                projectId = terminal.projectId,
                title = terminal.projectName,
                subLines = emptyList(),
                x = TERMINAL_LEFT, y = terminalTops.getValue(terminal.projectId), width = panelWidth,
                height = if (stacked) terminalHeight else 0,
                // The count is what the reader needs; the layer number never was — it is the
                // topological sort's own vocabulary, and it said "LAYER 0" for a build nothing
                // feeds, which reads as a rank rather than as a fact about edges.
                eyebrow = "FINAL PROJECT · ${index + 1} OF ${terminals.size}",
            )
        }
        var rightBottom = panelTop + drawnTerminals.size * (terminalHeight + TERMINAL_GAP) - TERMINAL_GAP
        if (hiddenTerminals > 0) {
            val moreY = rightBottom + TERMINAL_GAP
            nodes += GraphNode(
                key = "terminal-more",
                kind = NodeKind.TERMINAL,
                projectId = null,
                title = "+$hiddenTerminals more final ${if (hiddenTerminals == 1) "project" else "projects"}",
                subLines = listOf(SubLine("listed under the graph", Tone.MUTED)),
                x = TERMINAL_LEFT, y = moreY, width = panelWidth, height = TERMINAL_MORE_HEIGHT,
            )
            rightBottom = moreY + TERMINAL_MORE_HEIGHT
        }

        // --- the promised-supply tab ---------------------------------------------------------
        // Drawn only when something is promised: a tab standing for an empty table links to nothing.
        if (promised != null && promised.farms > 0) {
            val farms = if (promised.farms == 1) "THE FARM" else "THE ${promised.farms} FARMS"
            nodes += GraphNode(
                key = "promised",
                kind = NodeKind.PROMISED,
                projectId = null,
                title = "PROMISED SUPPLY · $farms YOU'RE BUILDING",
                subLines = listOf(SubLine("SEE TABLE ▸", Tone.ACCENT)),
                x = PROMISED_LEFT, y = PROMISED_TOP, width = PANEL_WIDTH - PROMISED_LEFT, height = PROMISED_HEIGHT,
            )
            edges += promisedRopes(promised, drawnTerminals, terminalTops, panelWidth)
        }

        // --- edges and intake rails ----------------------------------------------------------
        val rails = drawnTerminals.map { terminal ->
            railFor(terminal, terminalTops.getValue(terminal.projectId), terminalHeight, feeders)
        }
        edges += supplyRopes(feeders, drawnTerminals, rails)
        edges += rails.mapNotNull { rail ->
            rail.arrowY?.let {
                GraphEdge(
                    key = "${rail.key}-arrow",
                    path = "M ${rail.x} $it L ${TERMINAL_LEFT - 2} $it",
                    strokeWidth = 1.5,
                    dashed = false,
                )
            }
        }

        // The caption needs a line of its own below the column — computing the panel height
        // first and then placing the caption inside it put the text on top of the last node.
        val showCaption = producers.isNotEmpty()
        val captionY = columnBottom + CAPTION_GAP
        val contentBottom = if (showCaption) captionY + CAPTION_HEIGHT else columnBottom
        val height = maxOf(contentBottom, rightBottom) + PANEL_BOTTOM_PADDING
        val caption = if (showCaption) {
            BandCaption(
                "SUPPLY, NOT SEQUENCE — NOTHING HERE IS WAITING ON ANYTHING",
                x = 240,
                y = captionY,
            )
        } else {
            null
        }

        return Graph(
            nodes = nodes,
            edges = dropCollidingLabels(edges, nodes),
            groups = groups,
            rails = rails,
            width = PANEL_WIDTH,
            height = height,
            bandCaption = caption,
            bundled = bundledAny,
        )
    }

    // ---- pieces --------------------------------------------------------------------------

    /**
     * The project everything drains into: deepest layer first, then the most incoming edges,
     * then name for a stable answer between renders. Null when nothing has an edge at all.
     */
    internal fun terminalOf(roadmap: Roadmap): RoadmapNode? {
        if (roadmap.edges.isEmpty()) return null
        val fanIn = roadmap.edges.groupingBy { it.fromNodeId }.eachCount()
        return roadmap.nodes
            .filter { fanIn.containsKey(it.projectId) }
            .maxWithOrNull(
                compareBy<RoadmapNode> { it.layer }
                    .thenBy { fanIn[it.projectId] ?: 0 }
                    .thenByDescending { it.projectName }
            )
    }

    /**
     * Every project the world drains into, largest demand first (MCO-563).
     *
     * A final project is still to do, nothing consumes from it, and it has something to consume: a
     * supply line, or anything left to gather by hand ([demand], each project's planned demand from
     * farms plus by hand). A finished build at the end of a chain is history, not somewhere the
     * world is heading. This used to be [terminalOf] alone, which picks one. A second project fed by
     * the same farms then lost the tie-break, fell into the sequence band as "Start here", and had
     * its supply drawn into another project's panel.
     *
     * A supply line used to be required as well, so a 23,000-item castle no farm could help with was
     * filed under "not in any chain — do them whenever", while the same castle with one 1,500-wheat
     * line got a panel. What decides a destination is the work left, not whether a farm touches it.
     * Ranked by [demand] where it is known, since a hand-only project has no edges to sum.
     *
     * A world with no supply lines at all still has no graph: that is the fresh world, whose design
     * is not settled. Falls back to [terminalOf] when nothing unfinished is a sink, so a world where
     * everything is built still draws its graph.
     */
    internal fun terminalsOf(roadmap: Roadmap, demand: Map<Int, Long> = emptyMap()): List<RoadmapNode> {
        if (roadmap.edges.isEmpty()) return emptyList()
        val producers = roadmap.edges.mapTo(mutableSetOf()) { it.toNodeId }
        val edgeDemand = roadmap.edges
            .groupBy { it.fromNodeId }
            .mapValues { (_, edges) -> edges.sumOf { it.quantity ?: 0L } }
        // Ordered by how much of each the world **already supplies** (MCO-566), which is also what
        // decides the three drawn as panels. Demand says what makes a project final; supply says
        // which of them the reader can act on, and that is the comparison the panels are for.
        // Total demand stays as the tie-break, so two unfed builds still sort by size.
        val built = roadmap.nodes.filter { it.state == ProjectState.DONE }.mapTo(mutableSetOf()) { it.projectId }
        val farmSupply = roadmap.edges
            .filter { it.toNodeId in built }
            .groupBy { it.fromNodeId }
            .mapValues { (_, edges) -> edges.sumOf { it.quantity ?: 0L } }
        // Then by what the farms still to build will supply (frame 5A). With nothing producing every
        // final project ties at zero above, and demand alone put a 100,000-item hand-only build ahead
        // of one six of the farms being built will feed.
        val unfinished = roadmap.nodes.filter { !it.state.isTerminal }.mapTo(mutableSetOf()) { it.projectId }
        val promisedSupply = roadmap.edges
            .filter { it.toNodeId in unfinished }
            .groupBy { it.fromNodeId }
            .mapValues { (_, edges) -> edges.sumOf { it.quantity ?: 0L } }

        val sinks = roadmap.nodes
            .filter { it.projectId in edgeDemand || (demand[it.projectId] ?: 0L) > 0 }
            .filter { it.projectId !in producers }
            .filter { !it.state.isTerminal }
            .sortedWith(
                compareByDescending<RoadmapNode> { farmSupply[it.projectId] ?: 0L }
                    .thenByDescending { promisedSupply[it.projectId] ?: 0L }
                    .thenByDescending { demand[it.projectId] ?: edgeDemand[it.projectId] ?: 0L }
                    .thenByDescending { it.layer }
                    .thenBy { it.projectName }
            )
        return sinks.ifEmpty { listOfNotNull(terminalOf(roadmap)) }
    }

    /**
     * Unfinished projects upstream of a final project — the farms still to build ([RoadmapToBuild]).
     *
     * *Upstream* is the rule that matters (MCO-563); connected to something is not enough. A
     * project that leads into no final project is not a step towards one, however many farms
     * feed it, and the old sequence band once called such a project "Start here".
     *
     * Terminal-state projects are excluded by [ProjectState.isTerminal] rather than by
     * checking DONE alone — a cancelled project is not work either, and must not be listed among
     * the things left to do.
     */
    internal fun unbuiltUpstreamOf(roadmap: Roadmap, terminals: List<RoadmapNode>): List<RoadmapNode> {
        val terminalIds = terminals.mapTo(mutableSetOf()) { it.projectId }
        val producersOf = roadmap.edges.groupBy({ it.fromNodeId }, { it.toNodeId })
        val upstream = mutableSetOf<Int>()
        val queue = ArrayDeque(terminalIds)
        while (queue.isNotEmpty()) {
            producersOf[queue.removeFirst()].orEmpty().forEach { if (upstream.add(it)) queue.add(it) }
        }
        return roadmap.nodes
            .filter { it.projectId !in terminalIds }
            .filter { !it.state.isTerminal }
            .filter { it.projectId in upstream }
            .sortedWith(compareBy({ it.layer }, { it.projectName }))
    }

    internal data class Bundle(
        val count: Int,
        val items: Long,
        val edges: Int,
        /** Summed over the bundled producers, so the bundle feeds every final project any of them does. */
        val itemsByTerminal: Map<Int, Long> = emptyMap(),
    )

    /**
     * One run of the supply column: the farms that feed exactly this destination.
     *
     * Grouping by destination is what removes the crossings (MCO-566). Drawn as one list, a farm
     * feeding the lower panel and a farm feeding the upper one sit in whatever order their totals
     * give, and their ropes cross on the way out. Grouped, each run sits beside the panel it
     * feeds, and only the run that feeds more than one panel can cross anything.
     */
    internal data class ColumnGroup(
        /** The panel these farms feed alone, or null for the run that feeds more than one. */
        val terminalId: Int?,
        val header: String,
        val note: String?,
        val producers: List<Producer>,
    )

    /**
     * Splits [producers] into one run per drawn final project plus a run for the farms feeding
     * several, in panel order.
     *
     * An empty run is kept rather than dropped: "FEEDS COPPER LIBRARY ONLY · NONE" is a fact about
     * the world — everything reaching that panel is shared with another — and a silently missing
     * group reads as a farm the page forgot.
     */
    internal fun groupByDestination(producers: List<Producer>, drawn: List<RoadmapNode>): List<ColumnGroup> {
        val drawnIds = drawn.map { it.projectId }
        val single = mutableMapOf<Int, MutableList<Producer>>()
        val many = mutableListOf<Producer>()

        producers.forEach { producer ->
            val fed = drawnIds.filter { (producer.itemsByTerminal[it] ?: 0L) > 0 }
            when {
                fed.size == 1 -> single.getOrPut(fed.single()) { mutableListOf() }.add(producer)
                fed.size > 1 -> many.add(producer)
                // No recorded split: the producer feeds whatever it is drawn against. With one
                // panel that is that panel; with several there is nothing to attribute it to.
                drawnIds.size == 1 -> single.getOrPut(drawnIds.single()) { mutableListOf() }.add(producer)
                else -> many.add(producer)
            }
        }

        // The header names the destination and the note carries the numbers. Both in the header
        // ran past the column on any real project name — "✓ DONE · FEEDS STORAGE SYSTEM YAMS ONLY
        // · 14 FARMS · 11,810 ITEMS" truncated to the word ONLY, losing exactly the counts it was
        // there to give.
        return buildList {
            drawn.forEach { terminal ->
                val group = single[terminal.projectId].orEmpty()
                add(
                    ColumnGroup(
                        terminalId = terminal.projectId,
                        header = "FEEDS ${terminal.projectName.uppercase()} ONLY",
                        note = if (group.isEmpty()) {
                            "none"
                        } else {
                            "${group.size} ${if (group.size == 1) "farm" else "farms"} · " +
                                "${format(group.sumOf { it.items })} items"
                        },
                        producers = group,
                    )
                )
            }
            if (many.isNotEmpty()) {
                add(
                    ColumnGroup(
                        terminalId = null,
                        header = "FEEDS MORE THAN ONE",
                        note = "${many.size} ${if (many.size == 1) "farm" else "farms"} · largest first",
                        producers = many,
                    )
                )
            }
        }
    }

    internal fun splitProducers(producers: List<Producer>, limit: Int = TOP_PRODUCERS): Pair<List<Producer>, Bundle?> {
        val sorted = producers.sortedWith(compareByDescending<Producer> { it.items }.thenBy { it.name })
        if (sorted.size <= limit + 1) return sorted to null
        val top = sorted.take(limit)
        val rest = sorted.drop(limit)
        return top to Bundle(
            count = rest.size,
            items = rest.sumOf { it.items },
            edges = rest.sumOf { it.edges },
            itemsByTerminal = rest
                .flatMap { it.itemsByTerminal.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, amounts) -> amounts.sum() },
        )
    }

    /**
     * "74,557 Cobblestone" when one material dominates a single-edge producer, otherwise the
     * roll-up. Naming the material is what makes the biggest ropes readable at a glance.
     */
    private fun producerSummary(producer: Producer): String = when {
        producer.edges == 1 && producer.largestItemName != null ->
            "${format(producer.largestItemQuantity ?: producer.items)} ${producer.largestItemName}"

        producer.edges > 5 ->
            "${format(producer.items)} items · ${producer.edges} edges"

        else ->
            "${format(producer.items)} items · ${producer.edges} kinds"
    }

    /**
     * One dashed rope from the promised-supply tab into each panel the unbuilt farms feed.
     *
     * Into the panel's top edge, not onto its intake rail: the rail counts what is supplying now,
     * and a promise landing among those dots would read as one more supplier. The top panel takes
     * a short drop straight out of the tab; a lower one is reached down the strip right of the
     * panels and along the gap above it, so no rope crosses a panel it does not feed.
     */
    private fun promisedRopes(
        promised: Promised,
        terminals: List<RoadmapNode>,
        tops: Map<Int, Int>,
        panelWidth: Int,
    ): List<GraphEdge> = terminals.mapIndexedNotNull { index, terminal ->
        val items = promised.itemsByTerminal[terminal.projectId]?.takeIf { it > 0 } ?: return@mapIndexedNotNull null
        val top = tops.getValue(terminal.projectId)
        val tabBottom = PROMISED_TOP + PROMISED_HEIGHT
        val path = if (index == 0) {
            val x = TERMINAL_LEFT + 84
            "M $x $tabBottom L $x ${top - 1}"
        } else {
            // Each lower panel's trunk sits a little further right, so two ropes never share a line.
            val trunk = TERMINAL_LEFT + panelWidth + 5 + (index - 1) * 4
            val x = TERMINAL_LEFT + 120
            val gapY = top - TERMINAL_GAP / 2
            "M $trunk $tabBottom L $trunk ${gapY - 8} Q $trunk $gapY ${trunk - 8} $gapY " +
                "L ${x + 8} $gapY Q $x $gapY $x ${top - 1}"
        }
        GraphEdge(
            key = "promised-${terminal.projectId}",
            path = path,
            strokeWidth = strokeWidthFor(items),
            dashed = true,
        )
    }

    /**
     * One rope per column node per panel it feeds, landing on that panel's [IntakeRail].
     *
     * **Dots follow column order, not size.** Ranking the landings would reorder them against the
     * column and put a crossing between every pair that disagreed; taking them top to bottom means
     * the only ropes that can cross are the shared run's, which diverge by definition.
     */
    private fun supplyRopes(
        feeders: List<Pair<GraphNode, Map<Int, Long>>>,
        terminals: List<RoadmapNode>,
        rails: List<IntakeRail>,
    ): List<GraphEdge> = buildList {
        terminals.forEachIndexed { panelIndex, terminal ->
            val rail = rails[panelIndex]
            val feeding = feeders.mapNotNull { (node, split) ->
                split[terminal.projectId]?.takeIf { it > 0 }?.let { node to it }
            }
            // Width is rank by items *within this panel*; position is column order.
            val byItems = feeding.sortedByDescending { it.second }.map { it.first.key }
            feeding.forEachIndexed { index, (node, _) ->
                val dotY = rail.dots.getOrNull(index) ?: return@forEachIndexed
                val fromY = node.y + node.height / 2
                add(
                    GraphEdge(
                        key = "supply-${node.key}-${terminal.projectId}",
                        path = "M $COLUMN_WIDTH $fromY C 420 $fromY ${rail.x - 120} $dotY ${rail.x} $dotY",
                        strokeWidth = rankedWidth(byItems.indexOf(node.key), feeding.size),
                        dashed = node.kind == NodeKind.HAND,
                        marker = false,
                    )
                )
            }
        }
    }

    /** The rail [terminal]'s ropes land on: one dot per feeder, one arrow into the panel. */
    private fun railFor(
        terminal: RoadmapNode,
        panelTop: Int,
        panelHeight: Int,
        feeders: List<Pair<GraphNode, Map<Int, Long>>>,
    ): IntakeRail {
        val feeding = feeders.filter { (_, split) -> (split[terminal.projectId] ?: 0L) > 0 }
        val count = feeding.size
        // The count includes what you gather by hand — it lands on the rail and is supply like any
        // other. Whether any *farm* feeds this panel is a different question, and the one a build
        // nothing in the world feeds needs answered: that panel reads "NO FARM INTAKE" instead.
        val farms = feeding.count { (node, _) -> node.kind != NodeKind.HAND }
        val top = panelTop + RAIL_INSET
        val bottom = panelTop + panelHeight - RAIL_INSET
        val dots = when {
            count == 0 -> emptyList()
            count == 1 -> listOf((top + bottom) / 2)
            else -> (0 until count).map { top + it * (bottom - top) / (count - 1) }
        }
        return IntakeRail(
            key = "rail-${terminal.projectId}",
            x = RAIL_X,
            top = dots.firstOrNull() ?: top,
            bottom = dots.lastOrNull() ?: top,
            // Stated rather than left blank: a build no farm feeds is a fact about the world, and
            // an empty rail with no label reads as a panel the page failed to draw.
            label = if (farms == 0) {
                "NO FARM INTAKE"
            } else {
                "INTAKE · $count ${if (count == 1) "SUPPLIER" else "SUPPLIERS"}"
            },
            // Just above the first dot, beside the panel rather than over it: with no band the top
            // panel sits under the promised-supply tab, and a label above the panel ran into it.
            labelY = (dots.firstOrNull() ?: top) - 11,
            dots = dots,
            arrowY = dots.takeIf { it.isNotEmpty() }?.let { (it.first() + it.last()) / 2 },
        )
    }

    /**
     * The node's split, or — when it recorded none — the whole of [total] against every drawn
     * panel. An empty map means nothing was attributed, not that the node feeds nothing.
     */
    private fun Map<Int, Long>.orFedAgainst(drawn: List<RoadmapNode>, total: Long): Map<Int, Long> =
        ifEmpty { drawn.associate { it.projectId to total } }

    private fun producerSubLines(
        producer: Producer,
        terminalId: Int?,
        drawn: List<RoadmapNode>,
    ): List<SubLine> = if (terminalId != null) {
        listOf(SubLine("✓ ${producerSummary(producer)}"))
    } else {
        // A farm in the shared run is read for how it divides, not for its total: "17,760 to the
        // Library" is what explains why a second rope leaves it at all.
        splitSubLines(producer.itemsByTerminal, drawn)
            .ifEmpty { listOf(SubLine("✓ ${producerSummary(producer)}")) }
    }

    /** "→ Copper Library 17,760", one line per panel the node feeds, in panel order. */
    private fun splitSubLines(split: Map<Int, Long>, drawn: List<RoadmapNode>): List<SubLine> =
        drawn.mapNotNull { terminal ->
            split[terminal.projectId]?.takeIf { it > 0 }
                ?.let { SubLine("→ ${terminal.projectName} ${format(it)}") }
        }

    /** The share of the hand list the farms still to build would take, or null when it rounds to nothing. */
    internal fun promisedShareOf(hand: HandGathered): Long? {
        if (hand.items <= 0 || hand.promised <= 0) return null
        return ((hand.promised * 100) / hand.items).takeIf { it > 0 }
    }

    /** A node fits its title and one sub-line at [NODE_HEIGHT]; each further line adds a row. */
    private fun nodeHeightFor(subLines: Int): Int =
        NODE_HEIGHT + (subLines - 1).coerceAtLeast(0) * SUB_LINE_HEIGHT

    /**
     * Stroke width by **rank within one panel**: `4.5 − 3.5·(rank / (n − 1))`, clamped.
     *
     * Rank, not value. The log scale this replaces put everything above 20,000 items between 4.1
     * and 4.5 — differences no eye resolves — so Witch hut farm's 983 items into the Library drew
     * at nearly the width of its 84,193 into YAMS. What the reader compares is "which of these
     * feeds *this* build most", and rank answers exactly that in any world.
     */
    internal fun rankedWidth(rank: Int, count: Int): Double {
        if (count <= 1 || rank < 0) return MAX_STROKE
        val raw = MAX_STROKE - (MAX_STROKE - MIN_STROKE) * (rank.toDouble() / (count - 1))
        return ((raw * 10).roundToInt() / 10.0).coerceIn(MIN_STROKE, MAX_STROKE)
    }

    /**
     * Stroke width encodes items moved: `1 + 0.32·ln(items)`, clamped to `[1, 4.5]`.
     *
     * Logarithmic on purpose. Cobblestone at 74,557 and a single decorative block are five
     * orders of magnitude apart; drawn linearly every edge but one would be invisible.
     */
    internal fun strokeWidthFor(items: Long?): Double {
        if (items == null || items <= 1L) return 1.0
        val raw = 1.0 + 0.32 * ln(items.toDouble())
        return ((raw * 10).roundToInt() / 10.0).coerceIn(1.0, 4.5)
    }

    /**
     * Node divs paint above the SVG, so a label that lands inside a node box is simply
     * invisible — including in the 6px gutters between column nodes. Rather than nudging
     * labels around, drop any that intersects a node: a missing label costs a detail, a
     * clipped one looks like a rendering bug.
     */
    internal fun dropCollidingLabels(edges: List<GraphEdge>, nodes: List<GraphNode>): List<GraphEdge> {
        val boxes = nodes.map {
            val height = if (it.height > 0) it.height else TERMINAL_HEIGHT
            intArrayOf(it.x, it.y, it.x + it.width, it.y + height)
        }
        return edges.map { edge ->
            val label = edge.label ?: return@map edge
            // Rough text box: 10px mono is ~6px per character, 12px tall, sitting on the baseline.
            val halfWidth = label.text.length * 6
            val left = when (label.anchor) {
                "end" -> label.x - halfWidth
                "middle" -> label.x - halfWidth / 2
                else -> label.x
            }
            val right = left + if (label.anchor == "middle") halfWidth else halfWidth
            val top = label.y - 10
            val hit = boxes.any { (bx, by, bx2, by2) ->
                left < bx2 && right > bx && top < by2 && label.y > by
            }
            if (hit) edge.copy(label = null) else edge
        }
    }

    private operator fun IntArray.component1() = this[0]
    private operator fun IntArray.component2() = this[1]
    private operator fun IntArray.component3() = this[2]
    private operator fun IntArray.component4() = this[3]

    internal fun format(value: Long): String = "%,d".format(value)
}
