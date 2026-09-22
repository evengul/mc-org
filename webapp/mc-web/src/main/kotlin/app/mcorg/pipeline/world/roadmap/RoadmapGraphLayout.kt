package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapEdge
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
 * The panel reads as two bands, and keeping them apart is the point of the design:
 *
 * * **Sequence** (top): only projects that still need doing, left to right in the order to do
 *   them in.
 * * **Supply** (left column): finished farms feeding the terminal project. Explicitly *not* a
 *   queue — nothing there is waiting on anything.
 *
 * Nothing done ever enters the sequence band and nothing unbuilt ever enters the supply column.
 * That rule is what makes the page answer "what is left" rather than "what exists".
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

    const val SEQUENCE_TOP = 8
    const val SEQUENCE_HEIGHT = 78

    /** Where the sequence band starts — clear of the first supply edge's curve. */
    const val SEQUENCE_LEFT = 352

    /** Right edge of the sequence band; the terminal panel starts 40px later. */
    const val SEQUENCE_RIGHT = 756
    const val SEQUENCE_GAP = 18

    const val TERMINAL_LEFT = 796
    const val TERMINAL_WIDTH = 236
    const val TERMINAL_TOP = 110

    /**
     * Fan-in arrowheads land on the terminal panel's left edge, spread down [FAN_IN_SPAN].
     *
     * A *span* rather than a fixed pitch: the column can hold up to [TOP_PRODUCERS] + a bundle
     * + the by-hand node, and a fixed 30px pitch would run the last arrowheads off the bottom
     * of the panel they are supposed to be pointing at.
     */
    const val FAN_IN_X = 790
    const val FAN_IN_TOP = 150
    const val FAN_IN_SPAN = 190

    /**
     * A final project's panel height. A single panel grows with its content, as 2A draws it, and
     * this is only its nominal height for label collisions. Stacked panels (MCO-563) are placed
     * one below the other, so each gets exactly this height.
     */
    const val TERMINAL_HEIGHT = 210
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
     * Narrowest a sequence node may be before its title stops being readable — two words of
     * 13px mono, wrapped.
     */
    const val MIN_SEQUENCE_WIDTH = 120

    /**
     * How many nodes the band can hold, *derived* from the space it has.
     *
     * The band is a fixed-width strip, so it is O(1) in the same way the producer column is.
     * Forever world happens to have two projects here; a world mid-way through ten builds has
     * ten, and ten nodes across 400px is forty pixels each — a row of unreadable slivers.
     *
     * Derived rather than a constant because the two used to be set independently, and a cap
     * of four against a 404px band silently produced 66px nodes that ran off the end. Anything
     * past this collapses into one "+N more" node; the full order stays in the table view.
     */
    val MAX_SEQUENCE_SLOTS: Int =
        ((SEQUENCE_RIGHT - SEQUENCE_LEFT + SEQUENCE_GAP) / (MIN_SEQUENCE_WIDTH + SEQUENCE_GAP))
            .coerceAtLeast(1)

    private const val GROUP_HEADER_OFFSET = 16
    private const val PANEL_BOTTOM_PADDING = 20
    private const val CAPTION_GAP = 10
    private const val CAPTION_HEIGHT = 14

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
    )

    /** The numbers on the terminal panel, which come from the plan rather than the graph. */
    data class TerminalStats(
        val fromFarms: Long,
        val byHand: Long,
        val craftRows: Int,
        val openQuestions: Int,
        val percentComplete: Int,
    )

    // ---- outputs -----------------------------------------------------------------------

    enum class Tone { DEFAULT, MUTED, GREEN, RED, AMBER, ACCENT, DISABLED }

    enum class NodeKind { SEQUENCE, START, SUPPLY, BUNDLE, HAND, TERMINAL }

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
        /** Small tracked label above the title, e.g. "START HERE" or "TERMINAL · LAYER 3". */
        val eyebrow: String? = null,
        val eyebrowTone: Tone = Tone.MUTED,
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
        val kinds: Set<NodeKind>,
    )

    data class EdgeLabel(val text: String, val x: Int, val y: Int, val anchor: String)

    data class GraphEdge(
        val key: String,
        val path: String,
        val strokeWidth: Double,
        val dashed: Boolean,
        val label: EdgeLabel? = null,
    )

    data class Graph(
        val nodes: List<GraphNode>,
        val edges: List<GraphEdge>,
        val groups: List<GroupHeader>,
        val width: Int,
        val height: Int,
        val bandCaption: BandCaption?,
        /** True when the producer column was capped, so the template can offer the expander. */
        val bundled: Boolean,
    )

    data class BandCaption(val text: String, val x: Int, val y: Int)

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
    ): Graph? {
        val terminals = terminalsOf(roadmap, demand)
        if (terminals.isEmpty()) return null
        val drawnTerminals = terminals.take(MAX_TERMINALS)
        val hiddenTerminals = terminals.size - drawnTerminals.size

        val sequenceNodes = sequenceNodesOf(roadmap, terminals)
        val (topProducers, bundled) = splitProducers(producers)

        val nodes = mutableListOf<GraphNode>()
        val groups = mutableListOf<GroupHeader>()
        val edges = mutableListOf<GraphEdge>()

        // --- supply column ---------------------------------------------------------------
        var y = 0
        if (topProducers.isNotEmpty() || bundled != null) {
            val count = producers.size
            groups += GroupHeader(
                text = "✓ DONE · PRODUCING · $count ${if (count == 1) "FARM" else "FARMS"}",
                note = "largest first",
                tone = Tone.GREEN,
                y = 4,
                ruleY = 20,
                dashedRule = false,
                kinds = setOf(NodeKind.SUPPLY, NodeKind.BUNDLE),
            )
            y = 34
        }

        topProducers.forEach { producer ->
            nodes += GraphNode(
                key = "supply-${producer.projectId}",
                kind = NodeKind.SUPPLY,
                projectId = producer.projectId,
                title = producer.name,
                subLines = listOf(SubLine("✓ ${producerSummary(producer)}")),
                x = 0, y = y, width = COLUMN_WIDTH, height = NODE_HEIGHT,
            )
            y += PITCH
        }

        bundled?.let { bundle ->
            nodes += GraphNode(
                key = "supply-bundle",
                kind = NodeKind.BUNDLE,
                projectId = null,
                title = "✓ ${bundle.count} more, all done",
                subLines = listOf(
                    SubLine("${format(bundle.items)} items · ${bundle.edges} edges ▸", Tone.ACCENT),
                ),
                x = 0, y = y, width = COLUMN_WIDTH, height = NODE_HEIGHT,
            )
            y += PITCH
        }

        // --- the hand-gathered node, always last and always dashed ------------------------
        handGathered?.let { hand ->
            val headerY = y + 6
            groups += GroupHeader(
                text = "NOT A PROJECT · YOU GATHER IT",
                note = null,
                tone = Tone.MUTED,
                y = headerY,
                ruleY = headerY + GROUP_HEADER_OFFSET,
                dashedRule = true,
                kinds = setOf(NodeKind.HAND),
            )
            val handY = headerY + GROUP_HEADER_OFFSET + 12
            nodes += GraphNode(
                key = "hand",
                kind = NodeKind.HAND,
                projectId = null,
                title = "By hand",
                subLines = buildList {
                    add(SubLine("${format(hand.items)} items · ${hand.materials} materials"))
                    hand.concentration?.let { add(SubLine("⚠ $it", Tone.AMBER)) }
                },
                x = 0, y = handY, width = COLUMN_WIDTH, height = HAND_HEIGHT,
            )
            y = handY + HAND_HEIGHT
        }

        val columnBottom = y

        // --- sequence band ---------------------------------------------------------------
        // Capped like the producer column, and for the same reason: the band is a fixed-width
        // strip, so its node count has to be bounded or the design only works for the world it
        // was drawn against.
        // Everything fits, or one slot is given up to the tail node that counts the rest.
        val drawnCount = if (sequenceNodes.size <= MAX_SEQUENCE_SLOTS) {
            sequenceNodes.size
        } else {
            MAX_SEQUENCE_SLOTS - 1
        }
        val drawnSequence = sequenceNodes.take(drawnCount)
        val hiddenSequence = sequenceNodes.size - drawnSequence.size
        val slots = drawnSequence.size + if (hiddenSequence > 0) 1 else 0
        val seqWidth = sequenceWidth(slots)

        drawnSequence.forEachIndexed { index, node ->
            val x = SEQUENCE_LEFT + index * (seqWidth + SEQUENCE_GAP)
            val isStart = index == 0
            nodes += GraphNode(
                key = "seq-${node.projectId}",
                kind = if (isStart) NodeKind.START else NodeKind.SEQUENCE,
                projectId = node.projectId,
                title = node.projectName,
                subLines = sequenceSubLines(node),
                x = x, y = SEQUENCE_TOP, width = seqWidth, height = SEQUENCE_HEIGHT,
                eyebrow = if (isStart) "START HERE" else null,
                eyebrowTone = Tone.ACCENT,
            )
        }

        if (hiddenSequence > 0) {
            nodes += GraphNode(
                key = "seq-more",
                kind = NodeKind.SEQUENCE,
                projectId = null,
                title = "+$hiddenSequence more to build",
                subLines = listOf(SubLine("see the table view for the full order", Tone.MUTED)),
                x = SEQUENCE_LEFT + drawnSequence.size * (seqWidth + SEQUENCE_GAP),
                y = SEQUENCE_TOP, width = seqWidth, height = SEQUENCE_HEIGHT,
            )
        }

        // --- final project panels ------------------------------------------------------------
        // One per project the world drains into, largest demand first (MCO-563). A single panel
        // grows with its content, as 2A draws it; stacked panels need a known height, because
        // each is placed below the last.
        val stacked = drawnTerminals.size > 1
        val terminalTops = drawnTerminals.withIndex().associate { (index, terminal) ->
            terminal.projectId to TERMINAL_TOP + index * (TERMINAL_HEIGHT + TERMINAL_GAP)
        }
        drawnTerminals.forEach { terminal ->
            nodes += GraphNode(
                key = "terminal-${terminal.projectId}",
                kind = NodeKind.TERMINAL,
                projectId = terminal.projectId,
                title = terminal.projectName,
                subLines = emptyList(),
                x = TERMINAL_LEFT, y = terminalTops.getValue(terminal.projectId), width = TERMINAL_WIDTH,
                height = if (stacked) TERMINAL_HEIGHT else 0,
                eyebrow = "TERMINAL · LAYER ${terminal.layer}",
            )
        }
        var rightBottom = TERMINAL_TOP + drawnTerminals.size * (TERMINAL_HEIGHT + TERMINAL_GAP) - TERMINAL_GAP
        if (hiddenTerminals > 0) {
            val moreY = rightBottom + TERMINAL_GAP
            nodes += GraphNode(
                key = "terminal-more",
                kind = NodeKind.TERMINAL,
                projectId = null,
                title = "+$hiddenTerminals more final ${if (hiddenTerminals == 1) "project" else "projects"}",
                subLines = listOf(SubLine("see the table view for all of them", Tone.MUTED)),
                x = TERMINAL_LEFT, y = moreY, width = TERMINAL_WIDTH, height = TERMINAL_MORE_HEIGHT,
            )
            rightBottom = moreY + TERMINAL_MORE_HEIGHT
        }

        // --- edges -------------------------------------------------------------------------
        edges += sequenceEdges(drawnSequence, seqWidth, roadmap, drawnTerminals, terminalTops)
        edges += supplyEdges(nodes, topProducers, bundled, handGathered, drawnTerminals, terminalTops, stacked)

        // The caption needs a line of its own below the column — computing the panel height
        // first and then placing the caption inside it put the text on top of the last node.
        val showCaption = topProducers.isNotEmpty()
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
            width = PANEL_WIDTH,
            height = height,
            bandCaption = caption,
            bundled = bundled != null,
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
        val sinks = roadmap.nodes
            .filter { it.projectId in edgeDemand || (demand[it.projectId] ?: 0L) > 0 }
            .filter { it.projectId !in producers }
            .filter { !it.state.isTerminal }
            .sortedWith(
                compareByDescending<RoadmapNode> { demand[it.projectId] ?: edgeDemand[it.projectId] ?: 0L }
                    .thenByDescending { it.layer }
                    .thenBy { it.projectName }
            )
        return sinks.ifEmpty { listOfNotNull(terminalOf(roadmap)) }
    }

    /**
     * Unfinished projects upstream of a final project, in the order to build them.
     *
     * *Upstream* is the rule that matters (MCO-563); connected to something is not enough. A
     * project that leads into no final project is not a step towards one, however many farms
     * feed it, and putting it first in the band called it "Start here".
     *
     * Terminal-state projects are excluded by [ProjectState.isTerminal] rather than by
     * checking DONE alone — a cancelled project is not work either, and it must not take a
     * slot in a band whose whole claim is "these are the things left to do".
     */
    internal fun sequenceNodesOf(roadmap: Roadmap, terminals: List<RoadmapNode>): List<RoadmapNode> {
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

    private fun sequenceSubLines(node: RoadmapNode): List<SubLine> = buildList {
        if (node.isBlocked) add(SubLine("✕ not built yet", Tone.RED))
        val tasks = if (node.tasksTotal > 0) {
            "${node.tasksCompleted} / ${node.tasksTotal} tasks"
        } else {
            "no tasks"
        }
        add(SubLine("${node.state.name.lowercase()} · $tasks"))
    }

    /**
     * Width per slot, never below [MIN_SEQUENCE_WIDTH] — which is safe precisely because
     * [MAX_SEQUENCE_SLOTS] is derived from that minimum, so the clamp can never be the thing
     * that pushes a node off the end of the band.
     */
    internal fun sequenceWidth(count: Int): Int {
        if (count <= 0) return 0
        val span = SEQUENCE_RIGHT - SEQUENCE_LEFT - (count - 1) * SEQUENCE_GAP
        return (span / count).coerceAtLeast(MIN_SEQUENCE_WIDTH)
    }

    internal data class Bundle(
        val count: Int,
        val items: Long,
        val edges: Int,
        /** Summed over the bundled producers, so the bundle feeds every final project any of them does. */
        val itemsByTerminal: Map<Int, Long> = emptyMap(),
    )

    internal fun splitProducers(producers: List<Producer>): Pair<List<Producer>, Bundle?> {
        val sorted = producers.sortedWith(compareByDescending<Producer> { it.items }.thenBy { it.name })
        if (sorted.size <= TOP_PRODUCERS + 1) return sorted to null
        val top = sorted.take(TOP_PRODUCERS)
        val rest = sorted.drop(TOP_PRODUCERS)
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

    private fun sequenceEdges(
        sequence: List<RoadmapNode>,
        seqWidth: Int,
        roadmap: Roadmap,
        terminals: List<RoadmapNode>,
        terminalTops: Map<Int, Int>,
    ): List<GraphEdge> = buildList {
        // Between consecutive sequence nodes: a short hop, dashed when the ordering is a
        // hand-made one rather than a derived supply edge.
        //
        // **Only where an edge actually exists** (MCO-302). The band is *sorted* by layer, and
        // two projects sharing a layer are not sequenced relative to each other at all — an
        // arrow between them asserts an ordering nobody stated. This used to draw the hop
        // unconditionally and fall back to `dashed = edge?.itemName == null`, which for a
        // missing edge is `true`: a relationship that did not exist was painted in the exact
        // style reserved for a hand-made one. It only became visible once orderings could be
        // *removed* — before that the band's neighbours were always genuinely chained, so the
        // fallback never fired. Even caught it on the first delete: the graph still showed the
        // ordering he had just taken away.
        sequence.zipWithNext().forEachIndexed { index, (from, to) ->
            val edge = roadmap.edges.firstOrNull {
                it.fromNodeId == to.projectId && it.toNodeId == from.projectId
            } ?: return@forEachIndexed
            val fromRight = SEQUENCE_LEFT + index * (seqWidth + SEQUENCE_GAP) + seqWidth
            val toLeft = fromRight + SEQUENCE_GAP
            add(
                GraphEdge(
                    key = "seq-${from.projectId}-${to.projectId}",
                    path = "M $fromRight 47 L $toLeft 47",
                    strokeWidth = strokeWidthFor(edge.quantity),
                    dashed = edge.itemName == null,
                )
            )
        }

        // The last sequence node curves down into the terminal panel — again only if it really
        // feeds it. Same defect, same fix: a solid line into the terminal project is the
        // strongest claim on the page, and it was being drawn whether or not anything flowed.
        sequence.lastOrNull()?.let { last ->
            val index = sequence.lastIndex
            val right = SEQUENCE_LEFT + index * (seqWidth + SEQUENCE_GAP) + seqWidth
            // Into the first panel it feeds, in stack order (MCO-563). The top panel takes the
            // line on its top edge, as 2A draws it; a lower one takes it on its left edge, so the
            // line does not cross the panels above.
            val (terminalId, edge) = terminals.firstNotNullOfOrNull { terminal ->
                roadmap.edges
                    .firstOrNull { it.fromNodeId == terminal.projectId && it.toNodeId == last.projectId }
                    ?.let { terminal.projectId to it }
            } ?: return@let
            val top = terminalTops.getValue(terminalId)
            val path = if (top == TERMINAL_TOP) {
                "M $right 47 C ${right + 32} 47 ${TERMINAL_LEFT - 4} 96 ${TERMINAL_LEFT - 4} $TERMINAL_TOP"
            } else {
                "M $right 47 C ${right + 32} 47 ${FAN_IN_X - 40} ${top + 24} $FAN_IN_X ${top + 24}"
            }
            add(
                GraphEdge(
                    key = "seq-terminal",
                    path = path,
                    strokeWidth = strokeWidthFor(edge.quantity),
                    // A hand-made ordering carries no material, so it is dashed — as the legend
                    // says, and as the band's own hops already draw it.
                    dashed = edge.itemName == null,
                    label = seqTerminalLabel(edge)?.let { text ->
                        EdgeLabel(text = text, x = right - 8, y = TERMINAL_TOP + 2, anchor = "end")
                    },
                )
            )
        }
    }

    /**
     * The label on the line from the band into a final project.
     *
     * "5 Gunpowder · already covered" is 2A's label for a farm still being built whose output a
     * running farm already makes. It was printed on every such line regardless: "51,092 Cobblestone
     * · already covered" from the only cobblestone farm in the world, and "0 · already covered" on
     * a hand-made ordering that carries no material at all.
     *
     * The band only holds unfinished projects, so a line out of it that does not block can only
     * mean the item is already made elsewhere — that is when the suffix is true. A manual ordering
     * has no material to name, and gets no label.
     */
    internal fun seqTerminalLabel(edge: RoadmapEdge): String? {
        val item = edge.itemName ?: return null
        val amount = edge.quantity?.let { "${format(it)} " } ?: ""
        return if (edge.isBlocking) "$amount$item" else "$amount$item · already covered"
    }

    /**
     * Every supply-column node fans into the panel of each final project it feeds.
     *
     * With one final project that is every column node, spread down [FAN_IN_SPAN] as 2A draws it.
     * Stacked panels (MCO-563) take only the nodes that actually feed them, spread down their own
     * left edge, each line weighted by what that node sends to *that* project.
     */
    private fun supplyEdges(
        nodes: List<GraphNode>,
        topProducers: List<Producer>,
        bundle: Bundle?,
        hand: HandGathered?,
        terminals: List<RoadmapNode>,
        terminalTops: Map<Int, Int>,
        stacked: Boolean,
    ): List<GraphEdge> = buildList {
        val columnNodes = nodes.filter {
            it.kind == NodeKind.SUPPLY || it.kind == NodeKind.BUNDLE || it.kind == NodeKind.HAND
        }
        terminals.forEach { terminal ->
            val terminalId = terminal.projectId
            val feeding = columnNodes.mapNotNull { node ->
                val items = when (node.kind) {
                    NodeKind.SUPPLY -> topProducers
                        .firstOrNull { "supply-${it.projectId}" == node.key }
                        ?.let { it.itemsByTerminal.into(terminalId, it.items) }
                    NodeKind.BUNDLE -> bundle?.let { it.itemsByTerminal.into(terminalId, it.items) }
                    else -> hand?.let { it.itemsByTerminal.into(terminalId, it.items) }
                }
                items?.let { node to it }
            }
            val fanTop = if (stacked) terminalTops.getValue(terminalId) + 40 else FAN_IN_TOP
            val span = if (stacked) TERMINAL_HEIGHT - 60 else FAN_IN_SPAN
            val lastIndex = (feeding.size - 1).coerceAtLeast(1)
            feeding.forEachIndexed { index, (node, items) ->
                val fromY = node.y + node.height / 2
                val toY = fanTop + (index * span) / lastIndex
                add(
                    GraphEdge(
                        key = if (stacked) "supply-${node.key}-$terminalId" else "supply-${node.key}",
                        path = "M $COLUMN_WIDTH $fromY C 420 $fromY 560 $toY $FAN_IN_X $toY",
                        strokeWidth = strokeWidthFor(items),
                        dashed = node.kind == NodeKind.HAND,
                    )
                )
            }
        }
    }

    /**
     * What a column node sends into [terminalId], or null when it sends nothing there. An empty
     * map is a node that never recorded a split, so it feeds whatever it is drawn against.
     */
    private fun Map<Int, Long>.into(terminalId: Int, total: Long): Long? =
        if (isEmpty()) total else this[terminalId]?.takeIf { it > 0 }

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
