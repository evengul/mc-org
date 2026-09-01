package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapEdge
import app.mcorg.domain.model.world.RoadmapLayer
import app.mcorg.domain.model.world.RoadmapNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-469 — the graph layout, tested for the worlds it was *not* drawn against.
 *
 * The design was measured on one world: 26 projects at layer 0, a three-project spine, one
 * terminal. That world is the easy case. These tests lean on the shapes that would break a
 * layout tuned to it — a long sequence, a large producer set, several chains, no chain at all
 * — because "works on Forever world" is exactly the failure mode a hand-authored prototype
 * invites.
 */
class RoadmapGraphLayoutTest {

    private fun node(
        id: Int,
        name: String,
        state: ProjectState = ProjectState.ACTIVE,
        layer: Int = 0,
        tasksTotal: Int = 0,
        tasksCompleted: Int = 0,
    ) = RoadmapNode(
        projectId = id,
        projectName = name,
        projectType = ProjectType.BUILDING,
        stage = ProjectStage.PLANNING,
        state = state,
        tasksTotal = tasksTotal,
        tasksCompleted = tasksCompleted,
        isBlocked = false,
        blockingProjectIds = emptyList(),
        dependentProjectIds = emptyList(),
        layer = layer,
    )

    /** `consumer` depends on `producer` for [item]. */
    private fun edge(consumer: RoadmapNode, producer: RoadmapNode, item: String?, qty: Long?) =
        RoadmapEdge(
            fromNodeId = consumer.projectId,
            fromNodeName = consumer.projectName,
            toNodeId = producer.projectId,
            toNodeName = producer.projectName,
            isBlocking = producer.state != ProjectState.DONE,
            itemName = item,
            quantity = qty,
        )

    private fun roadmap(nodes: List<RoadmapNode>, edges: List<RoadmapEdge>) = Roadmap(
        worldId = 1,
        worldName = "Test world",
        nodes = nodes,
        edges = edges,
        layers = listOf(RoadmapLayer(0, nodes.map { it.projectId }, nodes.size)),
    )

    private val noStats = RoadmapGraphLayout.TerminalStats(0, 0, 0, 0, 0)

    private fun producer(id: Int, name: String, items: Long, edges: Int = 1) =
        RoadmapGraphLayout.Producer(id, name, items, edges)

    // ---- the shape the design assumes ------------------------------------------------

    @Test
    fun `a world with no edges has no graph to draw`() {
        val only = node(1, "Lonely build")
        val graph = RoadmapGraphLayout.of(roadmap(listOf(only), emptyList()), emptyList(), null, noStats)

        assertNull(graph, "no chain means the page falls back to its list sections")
    }

    @Test
    fun `the terminal is the deepest node anything drains into`() {
        val farm = node(1, "Cobble farm", ProjectState.DONE)
        val mid = node(2, "Slime farm", layer = 1)
        val build = node(3, "Storage", layer = 3)
        val edges = listOf(edge(mid, farm, "Cobblestone", 100), edge(build, mid, "Slimeball", 50))

        val terminal = RoadmapGraphLayout.terminalOf(roadmap(listOf(farm, mid, build), edges))

        assertEquals(3, terminal?.projectId)
    }

    // ---- generalisation: the band and the column are both bounded ----------------------

    @Test
    fun `a long sequence collapses its tail instead of drawing slivers`() {
        val terminal = node(99, "Storage", layer = 9)
        val queue = (1..9).map { node(it, "Build $it", layer = it) }
        val edges = queue.map { edge(terminal, it, "Thing", 10) }

        val graph = RoadmapGraphLayout.of(roadmap(queue + terminal, edges), emptyList(), null, noStats)
        assertNotNull(graph)

        val band = graph.nodes.filter {
            it.kind == RoadmapGraphLayout.NodeKind.SEQUENCE || it.kind == RoadmapGraphLayout.NodeKind.START
        }
        assertEquals(
            RoadmapGraphLayout.MAX_SEQUENCE_SLOTS,
            band.size,
            "the band fills its slots and no more, however long the queue",
        )
        assertTrue(
            band.any { it.title.endsWith("more to build") },
            "the tail is counted, not dropped",
        )
        assertTrue(
            band.all { it.width >= RoadmapGraphLayout.MIN_SEQUENCE_WIDTH },
            "no node is narrower than its title can survive",
        )
        // Every drawn node must stay inside the band's own span.
        band.forEach {
            assertTrue(
                it.x + it.width <= RoadmapGraphLayout.SEQUENCE_RIGHT,
                "${it.title} runs past the band at ${it.x + it.width}",
            )
        }
    }

    @Test
    fun `the producer column keeps the largest and bundles the rest`() {
        val producers = (1..20).map { producer(it, "Farm $it", it * 1000L) }
        val (top, bundle) = RoadmapGraphLayout.splitProducers(producers)

        assertEquals(RoadmapGraphLayout.TOP_PRODUCERS, top.size)
        assertEquals("Farm 20", top.first().name, "largest first")
        assertNotNull(bundle)
        assertEquals(15, bundle.count)
        assertEquals(producers.sumOf { it.items } - top.sumOf { it.items }, bundle.items)
    }

    @Test
    fun `a handful of producers is not bundled`() {
        val (top, bundle) = RoadmapGraphLayout.splitProducers((1..4).map { producer(it, "Farm $it", 10) })

        assertEquals(4, top.size)
        assertNull(bundle, "a small world should never meet a bundle node")
    }

    // ---- the load-bearing rule: done never in the band, unbuilt never in the column -----

    @Test
    fun `finished projects never enter the sequence band`() {
        val farm = node(1, "Cobble farm", ProjectState.DONE)
        val cancelled = node(2, "Abandoned", ProjectState.CANCELLED, layer = 1)
        val active = node(3, "Slime farm", layer = 1)
        val terminal = node(4, "Storage", layer = 2)
        val edges = listOf(
            edge(terminal, farm, "Cobblestone", 100),
            edge(terminal, cancelled, "Nothing", 1),
            edge(terminal, active, "Slimeball", 50),
        )

        val band = RoadmapGraphLayout.sequenceNodesOf(
            roadmap(listOf(farm, cancelled, active, terminal), edges),
            terminal,
        )

        assertEquals(listOf("Slime farm"), band.map { it.projectName })
    }

    // ---- edge weight -------------------------------------------------------------------

    @Test
    fun `stroke width is logarithmic and clamped`() {
        val hairline = RoadmapGraphLayout.strokeWidthFor(1)
        val small = RoadmapGraphLayout.strokeWidthFor(100)
        val big = RoadmapGraphLayout.strokeWidthFor(74_557)
        val absurd = RoadmapGraphLayout.strokeWidthFor(50_000_000)

        assertEquals(1.0, hairline)
        assertTrue(small in 1.0..4.5)
        assertTrue(big > small, "74,557 must read heavier than 100")
        assertEquals(4.5, absurd, "clamped, so one giant edge cannot swamp the panel")
        // Five orders of magnitude must not become five times the stroke.
        assertTrue(big / small < 3.0)
    }

    @Test
    fun `an edge with no quantity is a hairline, not an error`() {
        assertEquals(1.0, RoadmapGraphLayout.strokeWidthFor(null))
    }

    // ---- geometry stays inside the panel ------------------------------------------------

    @Test
    fun `a full column keeps every node and arrowhead inside the panel`() {
        val terminal = node(99, "Storage", layer = 2)
        val farms = (1..12).map { node(it, "Farm $it", ProjectState.DONE) }
        val edges = farms.map { edge(terminal, it, "Thing", 5_000) }
        val producers = farms.map { producer(it.projectId, it.projectName, 5_000) }
        val hand = RoadmapGraphLayout.HandGathered(57_336, 94, "Oak Log + Ice = 84%")

        val graph = RoadmapGraphLayout.of(roadmap(farms + terminal, edges), producers, hand, noStats)
        assertNotNull(graph)

        graph.nodes.forEach {
            assertTrue(it.x >= 0 && it.x + it.width <= graph.width, "${it.title} escapes sideways")
            assertTrue(it.y + it.height <= graph.height, "${it.title} escapes the bottom")
        }
        // The by-hand node is always last in the column and always dashed.
        val column = graph.nodes.filter { it.x == 0 }
        assertEquals(RoadmapGraphLayout.NodeKind.HAND, column.last().kind)
    }

    /**
     * The mobile fallback drops absolute positioning and reads document order, so a group
     * header has to know which nodes it introduces — emitting all headers first put both of
     * them above the producers, with the by-hand caption nowhere near the by-hand node.
     */
    @Test
    fun `every group header names the nodes it introduces`() {
        val terminal = node(99, "Storage", layer = 2)
        val farms = (1..3).map { node(it, "Farm $it", ProjectState.DONE) }
        val edges = farms.map { edge(terminal, it, "Thing", 500) }
        val producers = farms.map { producer(it.projectId, it.projectName, 500) }
        val hand = RoadmapGraphLayout.HandGathered(1_000, 12, null)

        val graph = RoadmapGraphLayout.of(roadmap(farms + terminal, edges), producers, hand, noStats)
        assertNotNull(graph)

        assertTrue(graph.groups.isNotEmpty())
        graph.groups.forEach { group ->
            assertTrue(group.kinds.isNotEmpty(), "'${group.text}' introduces nothing")
            assertTrue(
                graph.nodes.any { it.kind in group.kinds },
                "'${group.text}' introduces a kind no node has",
            )
        }
        // Every column node belongs to exactly one group, so none can be orphaned on mobile.
        val columnKinds = graph.nodes
            .filter { it.kind != RoadmapGraphLayout.NodeKind.TERMINAL }
            .filter { it.kind != RoadmapGraphLayout.NodeKind.START }
            .filter { it.kind != RoadmapGraphLayout.NodeKind.SEQUENCE }
            .map { it.kind }
        columnKinds.forEach { kind ->
            assertEquals(
                1,
                graph.groups.count { kind in it.kinds },
                "$kind must be introduced by exactly one header",
            )
        }
    }

    @Test
    fun `labels that would land behind a node are dropped rather than clipped`() {
        val covered = RoadmapGraphLayout.GraphNode(
            key = "n", kind = RoadmapGraphLayout.NodeKind.SUPPLY, projectId = 1,
            title = "Farm", subLines = emptyList(), x = 0, y = 0, width = 216, height = 56,
        )
        val hidden = RoadmapGraphLayout.GraphEdge(
            key = "e", path = "M 0 0", strokeWidth = 1.0, dashed = false,
            label = RoadmapGraphLayout.EdgeLabel("behind the node", x = 10, y = 30, anchor = "start"),
        )
        val visible = hidden.copy(
            key = "e2",
            label = RoadmapGraphLayout.EdgeLabel("in open canvas", x = 600, y = 400, anchor = "start"),
        )

        val result = RoadmapGraphLayout.dropCollidingLabels(listOf(hidden, visible), listOf(covered))

        assertNull(result.first { it.key == "e" }.label)
        assertNotNull(result.first { it.key == "e2" }.label)
    }
}
