package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapCycle
import app.mcorg.domain.model.world.RoadmapCycleOption
import app.mcorg.domain.model.world.RoadmapEdge
import app.mcorg.domain.model.world.RoadmapLayer
import app.mcorg.domain.model.world.RoadmapNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-571 — the farms still to build as a set (frame 4B), with order only where an edge says so
 * (frame 4C).
 *
 * The worlds here are cut-down versions of the fixture the frames were measured on: two final
 * projects fed by finished farms, and six farms mid-build that each feed one or both directly.
 */
class RoadmapToBuildTest {

    private fun node(id: Int, name: String, state: ProjectState = ProjectState.PENDING) = RoadmapNode(
        projectId = id,
        projectName = name,
        projectType = ProjectType.BUILDING,
        stage = ProjectStage.PLANNING,
        state = state,
        tasksTotal = 0,
        tasksCompleted = 0,
        isBlocked = false,
        blockingProjectIds = emptyList(),
        dependentProjectIds = emptyList(),
        layer = 0,
    )

    /** `consumer` depends on `producer` for [item]. */
    private fun edge(consumer: RoadmapNode, producer: RoadmapNode, item: String?, qty: Long?) = RoadmapEdge(
        fromNodeId = consumer.projectId,
        fromNodeName = consumer.projectName,
        toNodeId = producer.projectId,
        toNodeName = producer.projectName,
        isBlocking = producer.state != ProjectState.DONE,
        itemName = item,
        quantity = qty,
    )

    private fun roadmap(nodes: List<RoadmapNode>, edges: List<RoadmapEdge>, cycles: List<RoadmapCycle> = emptyList()) =
        Roadmap(
            worldId = 1,
            worldName = "Test world",
            nodes = nodes,
            edges = edges,
            layers = listOf(RoadmapLayer(0, nodes.map { it.projectId }, nodes.size)),
            cycles = cycles,
        )

    private val moss = node(1, "Moss farm", ProjectState.DONE)
    private val yams = node(10, "Storage System YAMS", ProjectState.ACTIVE)
    private val library = node(11, "Copper Library", ProjectState.ACTIVE)
    private val witch = node(20, "Witch hut farm", ProjectState.ACTIVE)
    private val cobble = node(21, "Cobble farm", ProjectState.ACTIVE)
    private val iron = node(22, "Earlygame iron farm", ProjectState.PAUSED)
    private val bartering = node(23, "Bartering setup")
    private val guardian = node(24, "Guardian farm")
    private val wither = node(25, "Wither rose farm")

    /** Frame 4B's world: nothing among the six waits on another. */
    private fun partiallyBuilt(extra: List<RoadmapEdge> = emptyList(), cycles: List<RoadmapCycle> = emptyList()) =
        roadmap(
            listOf(moss, yams, library, witch, cobble, iron, bartering, guardian, wither),
            listOf(
                edge(yams, moss, "Moss Block", 2_887),
                edge(library, moss, "Moss Block", 24),
                edge(yams, witch, "Redstone Dust", 84_194),
                edge(library, witch, "Redstone Dust", 983),
                edge(yams, cobble, "Cobblestone", 51_092),
                edge(yams, iron, "Iron Ingot", 30_000),
                edge(library, iron, "Iron Ingot", 2_999),
                edge(yams, bartering, "Quartz", 19_000),
                edge(library, bartering, "Quartz", 891),
                edge(yams, guardian, "Prismarine Shard", 85),
                edge(yams, wither, "Wither Rose", 2_000),
                edge(library, wither, "Wither Rose", 828),
            ) + extra,
            cycles,
        )

    private fun toBuildOf(world: Roadmap) = RoadmapToBuild.of(world, RoadmapGraphLayout.terminalsOf(world))

    // ---- 4B: a set, sorted by state then name -----------------------------------------------

    @Test
    fun `every unbuilt farm feeding a final project is listed, building first, then paused, then planned`() {
        val toBuild = toBuildOf(partiallyBuilt())

        assertEquals(
            listOf(
                "Cobble farm", "Witch hut farm",
                "Earlygame iron farm",
                "Bartering setup", "Guardian farm", "Wither rose farm",
            ),
            toBuild.rows.map { it.name },
        )
        assertTrue(toBuild.rows.none { it.name == "Moss farm" }, "a finished farm is supply, not work")
        assertTrue(toBuild.rows.all { it.depth == 0 && it.waitsOn.isEmpty() }, "nothing orders them")
        assertNull(toBuild.band, "no order, no band")
        assertTrue(toBuild.ordered.isEmpty())
    }

    @Test
    fun `a row names what it supplies in panel order, and sums those supply lines`() {
        val rows = toBuildOf(partiallyBuilt()).rows.associateBy { it.name }

        val witchRow = rows.getValue("Witch hut farm")
        assertEquals(listOf("Storage System YAMS", "Copper Library"), witchRow.supplies)
        assertEquals("Storage System YAMS + Copper Library", RoadmapToBuild.suppliesText(witchRow.supplies))
        assertEquals(85_177, witchRow.items)

        val cobbleRow = rows.getValue("Cobble farm")
        assertEquals("Storage System YAMS only", RoadmapToBuild.suppliesText(cobbleRow.supplies))
    }

    @Test
    fun `a farm sending one material names it, so a small number keeps its unit`() {
        val guardianRow = toBuildOf(partiallyBuilt()).rows.single { it.name == "Guardian farm" }

        assertEquals("85 Prismarine Shard", RoadmapToBuild.itemsText(guardianRow))
    }

    @Test
    fun `promised supply is what the unbuilt farms together send each panel`() {
        val toBuild = toBuildOf(partiallyBuilt())

        assertEquals(84_194L + 51_092 + 30_000 + 19_000 + 85 + 2_000, toBuild.promisedByTerminal[yams.projectId])
        assertEquals(983L + 2_999 + 891 + 828, toBuild.promisedByTerminal[library.projectId])
    }

    @Test
    fun `the in-progress farms are what to finish first, and the copy says why there is no ranking`() {
        val toBuild = toBuildOf(partiallyBuilt())

        assertEquals(listOf("Cobble farm", "Witch hut farm"), toBuild.inProgress.map { it.name })
        assertEquals("◐ Both building", RoadmapToBuild.finishBadge(2))
        val (lead, order) = RoadmapToBuild.finishNotes(toBuild)
        assertTrue(lead.startsWith("Two of the six are already in progress."), lead)
        assertTrue(lead.endsWith("The other four are planned or paused."), lead)
        assertTrue(order.startsWith("None of them waits on another"), order)
    }

    @Test
    fun `with nothing started, nothing is recommended as first`() {
        val world = partiallyBuilt().let { w ->
            w.copy(nodes = w.nodes.map { if (it.state == ProjectState.ACTIVE && it.projectId >= 20) it.copy(state = ProjectState.PENDING) else it })
        }
        val toBuild = toBuildOf(world)

        assertTrue(toBuild.inProgress.isEmpty())
        assertTrue(RoadmapToBuild.finishNotes(toBuild).first().startsWith("None of the six is started yet."))
    }

    // ---- 4C: the band comes back for the chain only -------------------------------------------

    /** Fixture 5 after the cycle was answered cobble-first: the iron farm waits on the cobble farm. */
    private fun chained() = partiallyBuilt(extra = listOf(edge(iron, cobble, "Cobblestone", 3_787)))

    @Test
    fun `a chained farm sits under the farm it waits on, and says what for`() {
        val rows = toBuildOf(chained()).rows

        assertEquals(
            listOf(
                "Cobble farm", "Earlygame iron farm",
                "Witch hut farm",
                "Bartering setup", "Guardian farm", "Wither rose farm",
            ),
            rows.map { it.name },
            "the paused iron farm leaves its state group to sit under the cobble farm",
        )
        val ironRow = rows[1]
        assertEquals(1, ironRow.depth)
        assertEquals("waits on Cobble farm for 3,787 Cobblestone", RoadmapToBuild.waitsText(ironRow.waitsOn))
    }

    @Test
    fun `waiting on a farm does not change what a row says it supplies`() {
        val cobbleRow = toBuildOf(chained()).rows.single { it.name == "Cobble farm" }

        assertEquals(listOf("Storage System YAMS"), cobbleRow.supplies, "the iron farm is not a final project")
        assertEquals(51_092, cobbleRow.items, "3,787 to the iron farm is order, not supply to a build")
    }

    @Test
    fun `the band draws the chain and nothing else`() {
        val toBuild = toBuildOf(chained())

        assertEquals(setOf(cobble.projectId, iron.projectId), toBuild.ordered)
        assertEquals(1, toBuild.chains)
        val band = assertNotNull(toBuild.band)
        assertEquals(listOf("Cobble farm", "Earlygame iron farm"), band.nodes.sortedBy { it.x }.map { it.title })

        val (first, second) = band.nodes.sortedBy { it.x }
        assertEquals(RoadmapGraphLayout.NodeKind.START, first.kind, "where the chain starts")
        assertEquals("◐ building · nothing before it", first.subLines.single().text)
        assertEquals("‖ paused · waits on Cobble farm", second.subLines.single().text)
        assertTrue(second.x >= first.x + first.width, "the farm waited on is drawn first")

        val arrow = band.edges.single()
        assertEquals("3,787 Cobblestone", arrow.label?.text)
        assertTrue(!arrow.dashed, "a derived edge is solid")
        assertEquals("SEQUENCE · 2 OF THE 6 FARMS", band.bandCaption?.text)
    }

    @Test
    fun `an unbuilt farm whose output a running farm already covers is not waited on`() {
        // MCO-466: a planned farm does not block for an item an operational farm already supplies.
        val covered = edge(iron, cobble, "Cobblestone", 3_787).copy(isBlocking = false)
        val toBuild = toBuildOf(partiallyBuilt(extra = listOf(covered)))

        assertTrue(toBuild.ordered.isEmpty())
        assertNull(toBuild.band)
        assertTrue(toBuild.rows.all { it.depth == 0 })
    }

    @Test
    fun `two farms waiting on the same one are stacked, not chained to each other`() {
        val toBuild = toBuildOf(
            partiallyBuilt(
                extra = listOf(
                    edge(iron, cobble, "Cobblestone", 3_787),
                    edge(wither, cobble, "Cobblestone", 500),
                )
            )
        )
        val band = assertNotNull(toBuild.band)
        val byName = band.nodes.associateBy { it.title }

        assertEquals(byName.getValue("Earlygame iron farm").x, byName.getValue("Wither rose farm").x)
        assertTrue(byName.getValue("Earlygame iron farm").y != byName.getValue("Wither rose farm").y)
        assertEquals(2, band.edges.size, "one arrow per wait, none between the two that wait")
    }

    @Test
    fun `a deep chain is bounded, and every farm in it is still in the table`() {
        val chain = (30..37).map { node(it, "Link $it") }
        val final = node(99, "Megabase", ProjectState.ACTIVE)
        val done = node(98, "Old farm", ProjectState.DONE)
        val edges = listOf(edge(final, done, "Stone", 10), edge(final, chain.last(), "Thing", 10)) +
            chain.zipWithNext().map { (a, b) -> edge(b, a, "Thing", 10) }
        val toBuild = RoadmapToBuild.of(roadmap(chain + final + done, edges), listOf(final))

        assertEquals(chain.size, toBuild.rows.size)
        val band = assertNotNull(toBuild.band)
        assertTrue(band.nodes.size <= RoadmapToBuild.MAX_BAND_COLUMNS * RoadmapToBuild.MAX_BAND_ROWS)
        assertEquals(chain.size - band.nodes.size, toBuild.bandHidden)
        band.nodes.forEach { assertTrue(it.x + it.width <= band.width, "${it.title} runs off the band") }
    }

    @Test
    fun `a farm feeding only another farm says which, rather than nothing`() {
        val gunpowder = node(26, "Creeper farm")
        val toBuild = toBuildOf(
            partiallyBuilt(extra = listOf(edge(cobble, gunpowder, "Gunpowder", 20))).let { it.copy(nodes = it.nodes + gunpowder) }
        )
        val row = toBuild.rows.single { it.name == "Creeper farm" }

        assertEquals(listOf("Cobble farm"), row.supplies)
        assertEquals(1, toBuild.rows.single { it.name == "Cobble farm" }.depth, "the cobble farm now waits on it")
    }

    // ---- an unanswered loop is not an order -----------------------------------------------------

    @Test
    fun `before the cycle is answered both farms read unsettled, flat, and no band appears`() {
        // RoadmapCycles set the smaller claim aside, which leaves iron → cobble standing — exactly
        // the chain the page must not draw until somebody says which comes first.
        val cycle = RoadmapCycle(
            projectIds = listOf(cobble.projectId, iron.projectId),
            projectNames = listOf(cobble.projectName, iron.projectName),
            options = emptyList(),
            breaking = RoadmapCycleOption(
                cobble.projectId, cobble.projectName, iron.projectId, iron.projectName, "Iron Ingot", 1_856,
            ),
        )
        val toBuild = toBuildOf(partiallyBuilt(extra = listOf(edge(iron, cobble, "Cobblestone", 3_787)), cycles = listOf(cycle)))

        val unsettled = toBuild.rows.filter { it.unsettled }.map { it.name }.toSet()
        assertEquals(setOf("Cobble farm", "Earlygame iron farm"), unsettled)
        assertTrue(toBuild.rows.all { it.depth == 0 }, "neither is indented")
        assertTrue(toBuild.rows.all { it.waitsOn.isEmpty() })
        assertNull(toBuild.band, "the band appears the moment the cycle becomes a chain, not before")
        assertTrue(RoadmapToBuild.finishNotes(toBuild)[1].contains("the question above"))
    }

    // ---- the graph without a band ------------------------------------------------------------

    @Test
    fun `with farms producing, the graph has no band, only a promised tab roped into each panel it feeds`() {
        val world = partiallyBuilt()
        val toBuild = toBuildOf(world)
        val producers = listOf(
            RoadmapGraphLayout.Producer(
                1, "Moss farm", 2_911, 2, itemsByTerminal = mapOf(yams.projectId to 2_887L, library.projectId to 24L),
            ),
        )

        val graph = assertNotNull(
            RoadmapGraphLayout.of(
                world, producers, null,
                promised = RoadmapGraphLayout.Promised(toBuild.rows.size, toBuild.promisedByTerminal),
            )
        )

        assertTrue(
            graph.nodes.none {
                it.kind == RoadmapGraphLayout.NodeKind.START || it.kind == RoadmapGraphLayout.NodeKind.SEQUENCE
            },
            "the farms still to build are a table now, not a band",
        )
        assertTrue(graph.nodes.none { it.projectId in toBuild.rows.map { row -> row.projectId } }, "nor in the column")
        val tab = graph.nodes.single { it.kind == RoadmapGraphLayout.NodeKind.PROMISED }
        assertEquals("PROMISED SUPPLY · THE 6 FARMS YOU'RE BUILDING", tab.title)

        val ropes = graph.edges.filter { it.key.startsWith("promised-") }
        assertEquals(setOf("promised-10", "promised-11"), ropes.map { it.key }.toSet())
        assertTrue(ropes.all { it.dashed }, "promised supply is the legend's ┄")

        val panels = graph.nodes.filter { it.kind == RoadmapGraphLayout.NodeKind.TERMINAL }
        assertEquals(RoadmapGraphLayout.PROMISED_TERMINAL_TOP, panels.first().y, "panels rise into the band's old space")
        assertTrue(tab.y + tab.height < panels.first().y, "the tab sits above the top panel")
        panels.forEach { assertTrue(it.x + it.width < graph.width, "${it.title} leaves no room for the ropes") }
    }

    @Test
    fun `a panel nothing unbuilt feeds gets no promised rope`() {
        val castle = node(12, "Deepslate castle", ProjectState.ACTIVE)
        val world = partiallyBuilt().let { it.copy(nodes = it.nodes + castle) }
        val toBuild = RoadmapToBuild.of(world, RoadmapGraphLayout.terminalsOf(world, mapOf(castle.projectId to 23_000L)))

        val graph = assertNotNull(
            RoadmapGraphLayout.of(
                world, emptyList(), null, mapOf(castle.projectId to 23_000L),
                promised = RoadmapGraphLayout.Promised(toBuild.rows.size, toBuild.promisedByTerminal),
            )
        )

        assertTrue(graph.edges.none { it.key == "promised-12" })
    }
}
