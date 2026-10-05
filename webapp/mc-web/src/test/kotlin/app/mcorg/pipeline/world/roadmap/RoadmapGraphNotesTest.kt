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

/**
 * MCO-563 — the sentences around the graph, and the by-hand node that speaks for more than one
 * final project.
 */
class RoadmapGraphNotesTest {

    private fun node(id: Int, name: String, state: ProjectState = ProjectState.ACTIVE, layer: Int = 0) =
        RoadmapNode(
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
            layer = layer,
        )

    private fun edge(consumer: RoadmapNode, producer: RoadmapNode, item: String, qty: Long) = RoadmapEdge(
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

    // ---- start here ---------------------------------------------------------------------

    @Test
    fun `with nothing left to build, the note names the other final projects`() {
        val yams = node(1, "Storage System YAMS", layer = 1)
        val copper = node(2, "Copper Library", layer = 1)
        val third = node(3, "Beacon", layer = 1)

        assertEquals("Nothing is left to build before it.", readyNoteFor(listOf(yams)))
        assertEquals(
            "Nothing is left to build before it. Copper Library is ready too.",
            readyNoteFor(listOf(yams, copper)),
        )
        assertEquals(
            "Nothing is left to build before it. 2 more final projects are ready too.",
            readyNoteFor(listOf(yams, copper, third)),
        )
    }

    @Test
    fun `a finished final project is never where to start`() {
        val yams = node(1, "Storage System YAMS", ProjectState.DONE, layer = 1)
        val copper = node(2, "Copper Library", ProjectState.DONE, layer = 1)

        assertNull(startOf(listOf(yams, copper)), "a built world has nothing to start")
    }

    @Test
    fun `with nothing left to build, the first unfinished final project is where to start`() {
        val done = node(1, "Old storage", ProjectState.DONE, layer = 1)
        val copper = node(2, "Copper Library", layer = 1)

        assertEquals(copper, startOf(listOf(done, copper)))
    }

    // ---- the column ---------------------------------------------------------------------

    /**
     * Fixture 6's shape: a hand-made "YAMS before Copper Library" leaves Copper Library the only
     * final project drawn. The row printed "34,313 items from 22 farms" — one panel's items over the
     * world's farm count — beside a supply column of six.
     */
    @Test
    fun `the column holds the farms feeding what is drawn, not every farm in the world`() {
        val cobble = node(1, "Cobble farm", ProjectState.DONE)
        val trading = node(2, "First trading setup", ProjectState.DONE)
        val iron = node(3, "Iron farm", ProjectState.DONE)
        val yams = node(4, "Storage System YAMS", layer = 1)
        val copper = node(5, "Copper Library", layer = 2)
        val world = roadmap(
            listOf(cobble, trading, iron, yams, copper),
            listOf(
                edge(yams, cobble, "Cobblestone", 51_575),
                edge(yams, iron, "Iron Ingot", 33_529),
                edge(yams, trading, "Glass", 16_509),
                edge(copper, trading, "Glass", 17_376),
                RoadmapEdge(copper.projectId, copper.projectName, yams.projectId, yams.projectName, isBlocking = true),
            ),
        )

        val drawn = RoadmapGraphLayout.terminalsOf(world).map { it.projectId }.toSet()
        assertEquals(setOf(5), drawn, "YAMS feeds Copper Library by hand-made ordering, so it is not final")
        assertEquals(listOf(2), producersOf(world, drawn).map { it.projectId }, "the column holds the one farm feeding what is drawn")
        assertEquals(3, allProducersOf(world).size, "the world still has three farms — just not in this column")
    }

    // ---- by hand ------------------------------------------------------------------------

    @Test
    fun `a material two final projects both need is counted once`() {
        val hand = assertNotNull(
            handGatheredOf(
                mapOf(
                    3 to listOf(
                        HandMaterial("minecraft:oak_log", "Oak Log (Block)", 1_000),
                        HandMaterial("minecraft:ice", "Ice (Block)", 500),
                    ),
                    4 to listOf(HandMaterial("minecraft:oak_log", "Oak Log (Block)", 300)),
                )
            )
        )

        assertEquals(1_800, hand.items, "each project's hand list is separate work, so items add")
        assertEquals(2, hand.materials, "oak log is one thing to go and gather")
        assertEquals(mapOf(3 to 1_500L, 4 to 300L), hand.itemsByTerminal)
        assertEquals("Oak Log + Ice = 100%", hand.concentration)
    }

    @Test
    fun `nothing left to gather by hand is no node at all`() {
        assertNull(handGatheredOf(mapOf(3 to emptyList())))
    }
}
