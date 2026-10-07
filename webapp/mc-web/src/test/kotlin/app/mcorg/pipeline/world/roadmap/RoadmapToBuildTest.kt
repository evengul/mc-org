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

    /** The witch hut and cobble farms have something collected — frame 4B's "two building". */
    private val startedFarms = setOf(witch.projectId, cobble.projectId)

    private fun toBuildOf(world: Roadmap, started: Set<Int> = startedFarms) =
        RoadmapToBuild.of(world, RoadmapGraphLayout.terminalsOf(world), started)

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
    fun `ACTIVE with nothing collected or done reads planned, not building`() {
        // MCO-579: an import arrives ACTIVE whether or not anyone has touched it.
        val toBuild = toBuildOf(partiallyBuilt(), started = emptySet())

        assertTrue(toBuild.inProgress.isEmpty())
        assertEquals("○ planned", toBuild.rows.single { it.name == "Witch hut farm" }.label)
        assertEquals("‖ paused", toBuild.rows.single { it.name == "Earlygame iron farm" }.label, "paused is declared")
        assertTrue(RoadmapToBuild.finishNotes(toBuild).first().startsWith("None of the six is started yet."))
    }

    @Test
    fun `a planned farm with something collected is building`() {
        val toBuild = toBuildOf(partiallyBuilt(), started = setOf(bartering.projectId))

        assertEquals(listOf("Bartering setup"), toBuild.inProgress.map { it.name })
        assertEquals("Bartering setup", toBuild.rows.first().name, "building sorts first")
    }

    @Test
    fun `a final project past the panels reads planned or building by the same rule as a farm`() {
        assertEquals("○ planned", RoadmapToBuild.stateLabel(ProjectState.ACTIVE, started = false), "an untouched import")
        assertEquals("◐ building", RoadmapToBuild.stateLabel(ProjectState.PENDING, started = true))
        assertEquals("‖ paused", RoadmapToBuild.stateLabel(ProjectState.PAUSED, started = true))
    }

    // ---- 5A: nothing built yet -----------------------------------------------------------------

    /** Frame 5A's world, cut down: every farm planned, nothing ordered. */
    private fun nothingBuilt() = partiallyBuilt().let { world ->
        world.copy(nodes = world.nodes.map { if (it.projectId in 20..25) it.copy(state = ProjectState.PENDING) else it })
    }

    @Test
    fun `with one state and no order, the table groups by where each farm's output goes`() {
        val world = nothingBuilt()
        val terminals = RoadmapGraphLayout.terminalsOf(world)
        val grouped = assertNotNull(RoadmapToBuild.groupsOf(RoadmapToBuild.of(world, terminals), terminals))

        assertEquals("planned", grouped.stateWord)
        assertEquals(
            listOf(
                listOf("Storage System YAMS", "Copper Library") to
                    listOf("Bartering setup", "Earlygame iron farm", "Witch hut farm", "Wither rose farm"),
                listOf("Storage System YAMS") to listOf("Cobble farm", "Guardian farm"),
            ),
            grouped.groups.map { it.supplies to it.rows.map { row -> row.name } },
            "the farms feeding most first, then one group per final project, name order inside",
        )
        assertEquals(listOf("Copper Library"), grouped.emptyFinals, "no farm feeds the Library alone")
    }

    @Test
    fun `state that still separates the rows keeps the plain table`() {
        val world = partiallyBuilt()

        assertNull(RoadmapToBuild.groupsOf(toBuildOf(world), RoadmapGraphLayout.terminalsOf(world)))
    }

    @Test
    fun `order keeps the plain table, so a chained farm stays under the one it waits on`() {
        val world = nothingBuilt().let { it.copy(edges = it.edges + edge(iron, cobble, "Cobblestone", 3_787)) }
        val terminals = RoadmapGraphLayout.terminalsOf(world)

        assertNull(RoadmapToBuild.groupsOf(RoadmapToBuild.of(world, terminals), terminals))
    }

    @Test
    fun `with nothing producing, final projects rank by what the farms being built will supply`() {
        val slime = node(12, "New slime farm", ProjectState.ACTIVE)
        // The moss farm too: nothing produces, so every final project's supply ties at zero.
        val world = nothingBuilt().let { w ->
            w.copy(nodes = w.nodes.map { if (it.projectId == moss.projectId) it.copy(state = ProjectState.PENDING) else it } + slime)
        }
        // Demand alone would put the hand-only slime farm second: 100,000 against the Library's 7,000.
        val demand = mapOf(yams.projectId to 240_000L, slime.projectId to 100_000L, library.projectId to 7_000L)

        assertEquals(
            listOf("Storage System YAMS", "Copper Library", "New slime farm"),
            RoadmapGraphLayout.terminalsOf(world, demand).map { it.projectName },
        )
    }

    @Test
    fun `each final project knows how many of the farms being built will feed it`() {
        val toBuild = toBuildOf(nothingBuilt())

        assertEquals(6, toBuild.feedersByTerminal[yams.projectId])
        assertEquals(4, toBuild.feedersByTerminal[library.projectId])
    }

    // ---- 4C: the band comes back for the chain only -------------------------------------------

    /** Fixture 5 after the cycle was answered cobble-first: the iron farm waits on the cobble farm. */
    private fun chained() = partiallyBuilt(extra = listOf(edge(iron, cobble, "Cobblestone", 3_787)))

    @Test
    fun `a line of farms is numbered and listed first, each saying what it waits on`() {
        val rows = toBuildOf(chained()).rows

        assertEquals(
            listOf(
                "Cobble farm", "Earlygame iron farm",
                "Witch hut farm",
                "Bartering setup", "Guardian farm", "Wither rose farm",
            ),
            rows.map { it.name },
            "the paused iron farm leaves its state group to follow the cobble farm",
        )
        assertEquals(listOf(1, 2, null, null, null, null), rows.map { it.step })
        assertEquals("waits on Cobble farm for 3,787 Cobblestone", RoadmapToBuild.waitsText(rows[1].waitsOn))
    }

    @Test
    fun `waiting on a farm does not change what a row says it supplies`() {
        val cobbleRow = toBuildOf(chained()).rows.single { it.name == "Cobble farm" }

        assertEquals(listOf("Storage System YAMS"), cobbleRow.supplies, "the iron farm is not a final project")
        assertEquals(51_092, cobbleRow.items, "3,787 to the iron farm is order, not supply to a build")
    }

    @Test
    fun `a total order is a numbered chain, not a band`() {
        // 5B: every ordered farm has at most one farm right before it and one after.
        val toBuild = toBuildOf(chained())

        assertEquals(setOf(cobble.projectId, iron.projectId), toBuild.ordered)
        assertEquals(1, toBuild.chains)
        assertEquals(listOf("Cobble farm", "Earlygame iron farm"), toBuild.chain?.map { it.name })
        assertNull(toBuild.band, "a numbering needs no columns")
    }

    @Test
    fun `waits a longer path already implies are not order, so a fan of them is still one line`() {
        // World 22's shape: the bartering farm waits on the cobble and iron farms, and the tree farm
        // on all of them — but as an order each is one step after the farm right before it.
        val tree = node(26, "Tree farm")
        val world = partiallyBuilt(
            extra = listOf(
                edge(iron, cobble, "Cobblestone", 3_787),
                edge(bartering, iron, "Iron Ingot", 2_080),
                edge(bartering, cobble, "Cobblestone", 3_177),
                edge(tree, bartering, "Obsidian", 400),
                edge(tree, iron, "Iron Ingot", 1_044),
                edge(tree, cobble, "Cobblestone", 3_419),
                edge(yams, tree, "Cherry Log", 16),
            )
        ).let { it.copy(nodes = it.nodes + tree) }

        val toBuild = toBuildOf(world)

        assertEquals(
            listOf("Cobble farm", "Earlygame iron farm", "Bartering setup", "Tree farm"),
            toBuild.chain?.map { it.name },
        )
        assertEquals(
            listOf("Bartering setup", "Earlygame iron farm", "Cobble farm"),
            toBuild.rows.single { it.name == "Tree farm" }.waitsOn.map { it.name },
            "the table still names every farm it waits on, the one right before it first",
        )
    }

    @Test
    fun `the reduction keeps a wait only when no longer path says it`() {
        val reduced = RoadmapToBuild.transitiveReduction(listOf(2 to 1, 3 to 2, 3 to 1, 4 to 3, 4 to 1))

        assertEquals(mapOf(2 to listOf(1), 3 to listOf(2), 4 to listOf(3)), reduced)
        assertEquals(listOf(1, 2, 3, 4), RoadmapToBuild.totalOrderOf(setOf(1, 2, 3, 4), reduced))
    }

    @Test
    fun `a fork is not a total order`() {
        val reduced = RoadmapToBuild.transitiveReduction(listOf(2 to 1, 3 to 1))

        assertNull(RoadmapToBuild.totalOrderOf(setOf(1, 2, 3), reduced))
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
        val band = assertNotNull(toBuild.band, "a fork is a partial order, so it keeps the band")
        assertNull(toBuild.chain)
        val byName = band.nodes.associateBy { it.title }

        assertEquals(byName.getValue("Earlygame iron farm").x, byName.getValue("Wither rose farm").x)
        assertTrue(byName.getValue("Earlygame iron farm").y != byName.getValue("Wither rose farm").y)
        assertEquals(2, band.edges.size, "one arrow per wait, none between the two that wait")

        val cobbleNode = byName.getValue("Cobble farm")
        assertEquals(RoadmapGraphLayout.NodeKind.START, cobbleNode.kind, "where the order starts")
        assertEquals("◐ building · nothing before it", cobbleNode.subLines.single().text)
        assertEquals("‖ paused · waits on Cobble farm", byName.getValue("Earlygame iron farm").subLines.single().text)
        assertTrue(band.edges.all { !it.dashed }, "derived edges are solid")
        assertEquals("SEQUENCE · 3 OF THE 6 FARMS", band.bandCaption?.text)
    }

    @Test
    fun `a deep line is a numbered chain, every farm in it`() {
        val chain = (30..37).map { node(it, "Link $it") }
        val final = node(99, "Megabase", ProjectState.ACTIVE)
        val done = node(98, "Old farm", ProjectState.DONE)
        val edges = listOf(edge(final, done, "Stone", 10), edge(final, chain.last(), "Thing", 10)) +
            chain.zipWithNext().map { (a, b) -> edge(b, a, "Thing", 10) }
        val toBuild = RoadmapToBuild.of(roadmap(chain + final + done, edges), listOf(final))

        assertEquals(chain.map { it.projectName }, toBuild.chain?.map { it.name }, "a list has no column cap")
        assertEquals((1..8).toList(), toBuild.rows.map { it.step })
    }

    @Test
    fun `a deep partial order wraps to another row of columns instead of hiding farms`() {
        // Two roots make it partial; five levels deep is one past the four columns a row holds.
        val links = (30..34).map { node(it, "Link $it") }
        val other = node(40, "Side farm")
        val final = node(99, "Megabase", ProjectState.ACTIVE)
        val done = node(98, "Old farm", ProjectState.DONE)
        val edges = listOf(
            edge(final, done, "Stone", 10),
            edge(final, links.last(), "Thing", 10),
            edge(links[1], other, "Thing", 10),
        ) + links.zipWithNext().map { (a, b) -> edge(b, a, "Thing", 10) }
        val toBuild = RoadmapToBuild.of(roadmap(links + other + final + done, edges), listOf(final))

        assertNull(toBuild.chain)
        val band = assertNotNull(toBuild.band)
        assertEquals(0, toBuild.bandHidden, "every ordered farm is drawn")
        val deepest = band.nodes.single { it.title == "Link 34" }
        assertTrue(deepest.y > band.nodes.single { it.title == "Link 30" }.y, "the fifth level starts a new row")
        band.nodes.forEach { assertTrue(it.x + it.width <= band.width, "${it.title} runs off the band") }
        assertTrue(
            band.height - (deepest.y + deepest.height) < 62,
            "the wrapped block holds one row, so the band ends there rather than a row lower",
        )
        assertTrue(band.edges.all { it.label == null }, "four columns leave no room between them for a label")
    }

    @Test
    fun `no arrow in the band runs through a farm, whether it skips a column or wraps to the next row`() {
        // Six deep, so it wraps; three farms at the first level, so the columns have rows; one wait
        // skipping a column inside the first row of columns (Hopper → Link 32), and one crossing into
        // the second row of columns to a column further right (Side farm → Link 35).
        val links = (30..35).map { node(it, "Link $it") }
        val side = node(40, "Side farm")
        val hopper = node(41, "Hopper farm")
        val final = node(99, "Megabase", ProjectState.ACTIVE)
        val edges = listOf(
            edge(final, links.last(), "Thing", 10),
            edge(links[5], side, "Thing", 10),
            edge(links[2], hopper, "Thing", 10),
        ) + links.zipWithNext().map { (a, b) -> edge(b, a, "Thing", 10) }
        val band = assertNotNull(RoadmapToBuild.of(roadmap(links + side + hopper + final, edges), listOf(final)).band)
        assertEquals(8, band.nodes.size)

        band.edges.forEach { edge ->
            pointsAlong(edge.path).forEach { (x, y) ->
                band.nodes.forEach { node ->
                    val inside = x > node.x + 1 && x < node.x + node.width - 1 && y > node.y + 1 && y < node.y + node.height - 1
                    assertTrue(!inside, "${edge.key} runs through ${node.title} at ($x, $y): ${edge.path}")
                }
            }
            pointsAlong(edge.path).forEach { (_, y) -> assertTrue(y <= band.height, "${edge.key} runs below the band") }
        }
    }

    /** Points every pixel or so along an SVG path made of `M`, `L` and `C`. */
    private fun pointsAlong(path: String): List<Pair<Double, Double>> {
        val tokens = path.split(" ").filter { it.isNotBlank() }
        val points = mutableListOf<Pair<Double, Double>>()
        var at = 0.0 to 0.0
        var i = 0
        fun num() = tokens[i++].toDouble()
        while (i < tokens.size) {
            when (tokens[i++]) {
                "M" -> at = num() to num()
                "L" -> {
                    val to = num() to num()
                    val steps = maxOf(1, maxOf(kotlin.math.abs(to.first - at.first), kotlin.math.abs(to.second - at.second)).toInt())
                    (0..steps).forEach { s ->
                        val t = s.toDouble() / steps
                        points += (at.first + (to.first - at.first) * t) to (at.second + (to.second - at.second) * t)
                    }
                    at = to
                }
                "C" -> {
                    val c1 = num() to num()
                    val c2 = num() to num()
                    val to = num() to num()
                    (0..200).forEach { s ->
                        val t = s / 200.0
                        val u = 1 - t
                        points += (u * u * u * at.first + 3 * u * u * t * c1.first + 3 * u * t * t * c2.first + t * t * t * to.first) to
                            (u * u * u * at.second + 3 * u * u * t * c1.second + 3 * u * t * t * c2.second + t * t * t * to.second)
                    }
                    at = to
                }
                else -> error("unexpected path token in $path")
            }
        }
        return points
    }

    @Test
    fun `a farm feeding only another farm says which, rather than nothing`() {
        val gunpowder = node(26, "Creeper farm")
        val toBuild = toBuildOf(
            partiallyBuilt(extra = listOf(edge(cobble, gunpowder, "Gunpowder", 20))).let { it.copy(nodes = it.nodes + gunpowder) }
        )
        val row = toBuild.rows.single { it.name == "Creeper farm" }

        assertEquals(listOf("Cobble farm"), row.supplies)
        assertEquals(listOf("Creeper farm", "Cobble farm"), toBuild.chain?.map { it.name }, "the cobble farm now waits on it")
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

    @Test
    fun `a line hanging off an unanswered loop is not numbered, since the loop's other farm may come first`() {
        // Bartering waits on the iron farm, and nothing else orders them — a line of two, except
        // that the cobble farm may yet come before the iron farm.
        val cycle = RoadmapCycle(
            projectIds = listOf(cobble.projectId, iron.projectId),
            projectNames = listOf(cobble.projectName, iron.projectName),
            options = emptyList(),
            breaking = RoadmapCycleOption(
                cobble.projectId, cobble.projectName, iron.projectId, iron.projectName, "Iron Ingot", 1_856,
            ),
        )
        val world = partiallyBuilt(
            extra = listOf(edge(iron, cobble, "Cobblestone", 3_787), edge(bartering, iron, "Iron Ingot", 2_080)),
            cycles = listOf(cycle),
        )

        val toBuild = toBuildOf(world)

        assertNull(toBuild.chain)
        assertTrue(toBuild.rows.all { it.step == null })
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
        assertEquals(RoadmapGraphLayout.TERMINAL_TOP, panels.first().y, "panels rise into the band's old space")
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
