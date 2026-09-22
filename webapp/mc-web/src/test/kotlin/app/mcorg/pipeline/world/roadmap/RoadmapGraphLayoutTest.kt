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

    private fun producer(id: Int, name: String, items: Long, edges: Int = 1) =
        RoadmapGraphLayout.Producer(id, name, items, edges)

    // ---- the shape the design assumes ------------------------------------------------

    @Test
    fun `a world with no edges has no graph to draw`() {
        val only = node(1, "Lonely build")
        val graph = RoadmapGraphLayout.of(roadmap(listOf(only), emptyList()), emptyList(), null)

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

        val graph = RoadmapGraphLayout.of(roadmap(queue + terminal, edges), emptyList(), null)
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
            listOf(terminal),
        )

        assertEquals(listOf("Slime farm"), band.map { it.projectName })
    }

    /**
     * MCO-302 — the band is *sorted* by layer, not chained by it.
     *
     * Two unfinished projects that share a layer have no ordering between them, and an arrow
     * joining them asserts one. The hop used to be drawn for every consecutive pair whether or
     * not an edge existed, with `dashed = edge?.itemName == null` falling through to `true` for
     * a missing edge — so a relationship that did not exist was painted in the very style
     * reserved for a hand-made ordering.
     *
     * It stayed invisible until orderings could be removed: before that, the band's neighbours
     * were always genuinely chained. The first delete on the real world drew the ordering that
     * had just been taken away.
     */
    @Test
    fun `two projects on the same layer are not joined by an invented arrow`() {
        val farm = node(1, "Cobble farm", ProjectState.DONE)
        val left = node(2, "Ghast farm")
        val right = node(3, "Slime farm")
        val terminal = node(4, "Storage", layer = 1)
        // Both unfinished projects hang off the same finished farm, and neither feeds the
        // other or the terminal — so nothing sequences them.
        val edges = listOf(
            edge(left, farm, "Cobblestone", 100),
            edge(right, farm, "Cobblestone", 100),
            edge(terminal, farm, "Cobblestone", 100),
        )

        val graph = RoadmapGraphLayout.of(
            roadmap(listOf(farm, left, right, terminal), edges),
            listOf(producer(1, "Cobble farm", 100)),
            null,
        )

        assertNotNull(graph)
        assertTrue(
            graph.edges.none { it.key == "seq-2-3" },
            "nothing orders Ghast farm before Slime farm: ${graph.edges.map { it.key }}",
        )
        assertTrue(
            graph.edges.none { it.key == "seq-terminal" },
            "and neither of them feeds the terminal: ${graph.edges.map { it.key }}",
        )
    }

    @Test
    fun `a real ordering between band neighbours is still drawn, and dashed`() {
        val farm = node(1, "Cobble farm", ProjectState.DONE)
        val first = node(2, "Perimeter")
        val second = node(3, "Walls", layer = 1)
        val terminal = node(4, "Storage", layer = 2)
        val edges = listOf(
            edge(first, farm, "Cobblestone", 100),
            // A hand-made ordering: no item, no quantity. This is the one the band must draw.
            edge(second, first, null, null),
            edge(terminal, second, "Stone", 10),
        )

        val graph = RoadmapGraphLayout.of(
            roadmap(listOf(farm, first, second, terminal), edges),
            listOf(producer(1, "Cobble farm", 100)),
            null,
        )

        assertNotNull(graph)
        val hop = graph.edges.single { it.key == "seq-2-3" }
        assertTrue(hop.dashed, "a hand-made ordering reads as the legend's ┄, not as supply")
        assertNotNull(graph.edges.singleOrNull { it.key == "seq-terminal" })
    }

    // ---- more than one final project (MCO-563) ------------------------------------------

    /**
     * Forever world's shape, cut down: YAMS and Copper Library, both fed by finished farms,
     * neither by the other. Copper Library used to lose the tie-break and read "Start here".
     */
    private fun twoFinalProjects(): Roadmap {
        val cobble = node(1, "Cobble farm", ProjectState.DONE)
        val trading = node(2, "First trading setup", ProjectState.DONE)
        val yams = node(3, "Storage System YAMS", layer = 1)
        val copper = node(4, "Copper Library", layer = 1)
        return roadmap(
            listOf(cobble, trading, yams, copper),
            listOf(
                edge(yams, cobble, "Cobblestone", 51_575),
                edge(yams, trading, "Glass", 16_509),
                edge(copper, trading, "Glass", 17_376),
            ),
        )
    }

    private val producersOfTwo = listOf(
        RoadmapGraphLayout.Producer(1, "Cobble farm", 51_575, 1, itemsByTerminal = mapOf(3 to 51_575L)),
        RoadmapGraphLayout.Producer(
            2, "First trading setup", 33_885, 2, itemsByTerminal = mapOf(3 to 16_509L, 4 to 17_376L),
        ),
    )

    @Test
    fun `a second project that only consumes is a final project, not the start of the band`() {
        val world = twoFinalProjects()
        val terminals = RoadmapGraphLayout.terminalsOf(world)

        assertEquals(listOf(3, 4), terminals.map { it.projectId }, "largest demand first")
        assertTrue(
            RoadmapGraphLayout.sequenceNodesOf(world, terminals).isEmpty(),
            "nothing is left to build before either of them",
        )

        val graph = assertNotNull(RoadmapGraphLayout.of(world, producersOfTwo, null))
        assertTrue(
            graph.nodes.none {
                it.kind == RoadmapGraphLayout.NodeKind.START || it.kind == RoadmapGraphLayout.NodeKind.SEQUENCE
            },
            "Copper Library is not a step on the way to YAMS",
        )
        val panels = graph.nodes.filter { it.kind == RoadmapGraphLayout.NodeKind.TERMINAL }
        assertEquals(listOf("Storage System YAMS", "Copper Library"), panels.map { it.title })
        assertTrue(panels[1].y >= panels[0].y + panels[0].height, "the second panel sits below the first")
        panels.forEach { assertTrue(it.y + it.height <= graph.height, "${it.title} escapes the bottom") }
    }

    @Test
    fun `each farm draws a line to each final project it feeds, and only those`() {
        val graph = assertNotNull(RoadmapGraphLayout.of(twoFinalProjects(), producersOfTwo, null))

        assertEquals(
            setOf("supply-supply-1-3", "supply-supply-2-3", "supply-supply-2-4"),
            graph.edges.map { it.key }.filter { it.startsWith("supply-") }.toSet(),
            "Cobble farm feeds YAMS alone; First trading setup feeds both",
        )
    }

    @Test
    fun `an unfinished project upstream of one final project is still in the band`() {
        val farm = node(1, "Cobble farm", ProjectState.DONE)
        val ghast = node(2, "Ghast farm")
        val yams = node(3, "Storage", layer = 1)
        val copper = node(4, "Copper Library", layer = 1)
        val world = roadmap(
            listOf(farm, ghast, yams, copper),
            listOf(
                edge(yams, ghast, "Gunpowder", 5),
                edge(yams, farm, "Cobblestone", 1_000),
                edge(copper, farm, "Cobblestone", 100),
            ),
        )

        assertEquals(
            listOf("Ghast farm"),
            RoadmapGraphLayout.sequenceNodesOf(world, RoadmapGraphLayout.terminalsOf(world)).map { it.projectName },
        )
    }

    @Test
    fun `final projects past the cap are counted, not dropped`() {
        val farm = node(1, "Cobble farm", ProjectState.DONE)
        val builds = (2..6).map { node(it, "Build $it", layer = 1) }
        val edges = builds.map { edge(it, farm, "Cobblestone", 100L * it.projectId) }

        val graph = assertNotNull(
            RoadmapGraphLayout.of(roadmap(listOf(farm) + builds, edges), listOf(producer(1, "Cobble farm", 2_000)), null)
        )

        val panels = graph.nodes.filter { it.kind == RoadmapGraphLayout.NodeKind.TERMINAL }
        assertEquals(RoadmapGraphLayout.MAX_TERMINALS, panels.count { it.projectId != null })
        assertEquals("+2 more final projects", panels.single { it.projectId == null }.title)
        panels.forEach { assertTrue(it.y + it.height <= graph.height, "${it.title} escapes the bottom") }
    }

    @Test
    fun `a world where every sink is finished still draws toward its deepest project`() {
        val farm = node(1, "Cobble farm", ProjectState.DONE)
        val build = node(2, "Old storage", ProjectState.DONE, layer = 1)
        val world = roadmap(listOf(farm, build), listOf(edge(build, farm, "Cobblestone", 100)))

        assertEquals(listOf(2), RoadmapGraphLayout.terminalsOf(world).map { it.projectId })
    }

    // ---- final projects no farm feeds -------------------------------------------------

    /** Fixture 3b: a castle whose every material is gathered by hand, beside a farm-fed YAMS. */
    @Test
    fun `a project with nothing but hand work is a final project`() {
        val farm = node(1, "Cobble farm", ProjectState.DONE)
        val yams = node(2, "Storage System YAMS", layer = 1)
        val castle = node(3, "Deepslate castle")
        val idea = node(4, "Redstone Crafting Area")
        val world = roadmap(listOf(farm, yams, castle, idea), listOf(edge(yams, farm, "Cobblestone", 51_575)))

        val terminals = RoadmapGraphLayout.terminalsOf(world, demand = mapOf(2 to 274_154L, 3 to 23_000L))

        assertEquals(listOf("Storage System YAMS", "Deepslate castle"), terminals.map { it.projectName })
        assertTrue(idea !in terminals, "nothing to gather is not somewhere the world is heading")
    }

    /**
     * Panels are ordered by what the world **already supplies** each of them (MCO-566), and that
     * same order picks the three drawn. Demand is only the tie-break: it says what makes a project
     * final, while supply says which of them the reader can act on today.
     *
     * Forever world's New slime farm is the case that pins it — 100,000 items of hand work and not
     * one farm feeding any of it, so for all its demand it sorts last.
     */
    @Test
    fun `final projects are ordered by the supply the world already has for them`() {
        val trading = node(1, "First trading setup", ProjectState.DONE)
        val yams = node(2, "Storage System YAMS", layer = 1)
        val copper = node(3, "Copper Library", layer = 1)
        val slime = node(4, "New slime farm")
        val world = roadmap(
            listOf(trading, yams, copper, slime),
            listOf(edge(yams, trading, "Glass", 16_509), edge(copper, trading, "Glass", 17_376)),
        )

        val terminals = RoadmapGraphLayout.terminalsOf(
            world,
            demand = mapOf(2 to 274_154L, 3 to 63_199L, 4 to 100_000L),
        )

        assertEquals(
            listOf("Copper Library", "Storage System YAMS", "New slime farm"),
            terminals.map { it.projectName },
            "the Library's 17,376 of standing supply edges out YAMS's 16,509, for all YAMS's demand",
        )
    }

    @Test
    fun `a world with no supply lines has no graph, whatever is left to gather`() {
        val castle = node(1, "Deepslate castle")

        assertTrue(RoadmapGraphLayout.terminalsOf(roadmap(listOf(castle), emptyList()), mapOf(1 to 23_000L)).isEmpty())
    }

    // ---- destination groups and intake rails (MCO-566) ---------------------------------

    /** Fixture 2b's shape, cut down: one farm feeding YAMS alone, one feeding both panels. */
    private fun twoPanelWorld(): Roadmap {
        val moss = node(1, "Moss farm", ProjectState.DONE)
        val trading = node(2, "First trading setup", ProjectState.DONE)
        val yams = node(3, "Storage System YAMS", layer = 1)
        val copper = node(4, "Copper Library", layer = 1)
        return roadmap(
            listOf(moss, trading, yams, copper),
            listOf(
                edge(yams, moss, "Moss Block", 2_887),
                edge(yams, trading, "Glass", 16_728),
                edge(copper, trading, "Glass", 17_760),
            ),
        )
    }

    private val twoPanelProducers = listOf(
        RoadmapGraphLayout.Producer(1, "Moss farm", 2_887, 1, itemsByTerminal = mapOf(3 to 2_887L)),
        RoadmapGraphLayout.Producer(
            2, "First trading setup", 34_488, 2, itemsByTerminal = mapOf(3 to 16_728L, 4 to 17_760L),
        ),
    )

    @Test
    fun `the column runs one group per panel, then the farms that feed more than one`() {
        val world = twoPanelWorld()
        val drawn = RoadmapGraphLayout.terminalsOf(world)
        val groups = RoadmapGraphLayout.groupByDestination(twoPanelProducers, drawn)

        assertEquals(listOf(3, 4, null), groups.map { it.terminalId }, "panel runs in panel order, shared last")
        assertEquals(listOf("Moss farm"), groups[0].producers.map { it.name })
        assertEquals("FEEDS STORAGE SYSTEM YAMS ONLY", groups[0].header)
        // The numbers live in the note: both in the header ran past the column and truncated.
        assertEquals("1 farm · 2,887 items", groups[0].note)
        assertTrue(groups[1].producers.isEmpty())
        assertEquals("none", groups[1].note, "an empty run is stated, not dropped")
        assertEquals(listOf("First trading setup"), groups[2].producers.map { it.name })
        assertEquals("FEEDS MORE THAN ONE", groups[2].header)
    }

    @Test
    fun `every rope lands on a rail, and each panel takes one arrow`() {
        val hand = RoadmapGraphLayout.HandGathered(
            60_000, 40, null, itemsByTerminal = mapOf(3 to 40_000L, 4 to 20_000L),
        )

        val graph = assertNotNull(RoadmapGraphLayout.of(twoPanelWorld(), twoPanelProducers, hand))

        assertEquals(2, graph.rails.size)
        val yamsRail = graph.rails.single { it.key == "rail-3" }
        assertEquals(3, yamsRail.dots.size, "moss, trading and the hand node all feed YAMS")
        assertEquals("INTAKE · 3 SUPPLIERS", yamsRail.label, "what you gather by hand is intake too")

        val ropes = graph.edges.filter { it.key.startsWith("supply-") }
        assertEquals(5, ropes.size, "three into YAMS, two into the Library")
        assertTrue(ropes.none { it.marker }, "a rope ends on a dot; the rail carries the arrowhead")
        assertEquals(2, graph.edges.count { it.key.endsWith("-arrow") }, "one arrow per panel, not seven")
    }

    /** Fixture 3b: the castle no farm feeds still gets a panel, and its rail says so. */
    @Test
    fun `a panel no farm feeds is labelled, and still takes what you gather by hand`() {
        val farm = node(1, "Cobble farm", ProjectState.DONE)
        val yams = node(2, "Storage System YAMS", layer = 1)
        val castle = node(3, "Deepslate castle")
        val world = roadmap(listOf(farm, yams, castle), listOf(edge(yams, farm, "Cobblestone", 51_575)))
        val hand = RoadmapGraphLayout.HandGathered(
            83_000, 60, null, itemsByTerminal = mapOf(2 to 60_000L, 3 to 23_000L),
        )
        val producers = listOf(
            RoadmapGraphLayout.Producer(1, "Cobble farm", 51_575, 1, itemsByTerminal = mapOf(2 to 51_575L)),
        )

        val graph = assertNotNull(
            RoadmapGraphLayout.of(world, producers, hand, demand = mapOf(2 to 274_154L, 3 to 23_000L))
        )

        val castleRail = graph.rails.single { it.key == "rail-3" }
        assertEquals("NO FARM INTAKE", castleRail.label)
        assertEquals(1, castleRail.dots.size, "what you gather by hand still arrives")
        assertNotNull(castleRail.arrowY)
    }

    @Test
    fun `rank spreads the rope widths where the log scale flattened them`() {
        assertEquals(4.5, RoadmapGraphLayout.rankedWidth(0, 4))
        assertEquals(1.0, RoadmapGraphLayout.rankedWidth(3, 4))
        assertEquals(4.5, RoadmapGraphLayout.rankedWidth(0, 1), "a lone supplier draws at full weight")

        // The two quantities that motivated the change: Witch hut farm sends 84,193 to one panel
        // and 983 to another, and the log scale drew them at nearly the same width.
        val flattened = RoadmapGraphLayout.strokeWidthFor(84_193) - RoadmapGraphLayout.strokeWidthFor(983)
        assertTrue(flattened < 1.4, "the old scale separated them by only $flattened")
        assertEquals(3.5, RoadmapGraphLayout.rankedWidth(0, 2) - RoadmapGraphLayout.rankedWidth(1, 2))
    }

    // ---- the line into a final project -------------------------------------------------

    @Test
    fun `the band's line into a final project says already covered only when it does not block`() {
        val slime = node(1, "Slime farm")
        val yams = node(2, "Storage System YAMS", layer = 1)

        assertEquals("50 Slimeball", RoadmapGraphLayout.seqTerminalLabel(edge(yams, slime, "Slimeball", 50)))
        assertEquals(
            "5 Gunpowder · already covered",
            RoadmapGraphLayout.seqTerminalLabel(edge(yams, slime, "Gunpowder", 5).copy(isBlocking = false)),
        )
        assertNull(RoadmapGraphLayout.seqTerminalLabel(edge(yams, slime, null, null)), "an ordering has no material")
    }

    /** Fixture 6: "YAMS before Copper Library" was drawn solid and labelled "0 · already covered". */
    @Test
    fun `a hand-made ordering into a final project is dashed and unlabelled`() {
        val trading = node(1, "First trading setup", ProjectState.DONE)
        val yams = node(2, "Storage System YAMS", layer = 1)
        val copper = node(3, "Copper Library", layer = 2)
        val world = roadmap(
            listOf(trading, yams, copper),
            listOf(
                edge(copper, yams, null, null),
                edge(copper, trading, "Glass", 17_376),
                edge(yams, trading, "Glass", 16_509),
            ),
        )

        val graph = assertNotNull(RoadmapGraphLayout.of(world, listOf(producer(1, "First trading setup", 17_376)), null))

        val line = assertNotNull(graph.edges.singleOrNull { it.key == "seq-terminal" })
        assertTrue(line.dashed, "a manual ordering is dashed, as the legend says")
        assertNull(line.label)
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

        val graph = RoadmapGraphLayout.of(roadmap(farms + terminal, edges), producers, hand)
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

        val graph = RoadmapGraphLayout.of(roadmap(farms + terminal, edges), producers, hand)
        assertNotNull(graph)

        assertTrue(graph.groups.isNotEmpty())
        val keys = graph.groups.map { it.key }
        assertEquals(keys.size, keys.distinct().size, "two headers share a key")

        // Every column node sits under exactly one header, so none is orphaned when the mobile
        // fallback drops absolute positioning and reads document order. A header with no nodes is
        // allowed and deliberate — "FEEDS X ONLY · NONE" is a fact about the world.
        graph.nodes
            .filter {
                it.kind == RoadmapGraphLayout.NodeKind.SUPPLY ||
                    it.kind == RoadmapGraphLayout.NodeKind.BUNDLE ||
                    it.kind == RoadmapGraphLayout.NodeKind.HAND
            }
            .forEach { node ->
                assertEquals(
                    1,
                    graph.groups.count { it.key == node.group },
                    "'${node.title}' must sit under exactly one header",
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
