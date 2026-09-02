package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapEdge
import app.mcorg.domain.model.world.RoadmapLayer
import app.mcorg.domain.model.world.RoadmapNode
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MCO-465 — what a "Depends on" cell prints when a producer supplies many resources.
 *
 * Rendered rather than integration-tested: the shape under test is entirely a template
 * decision (group, headline, fold) over a `Roadmap` the step already builds, and
 * WorldRoadmapIT covers the route that builds it. Same posture as [FarmScaleRollUpTest].
 *
 * The numbers here mirror the real *Forever world*: one trading hall supplying forty
 * single decorative blocks alongside a four-figure quartz demand, which is what made the
 * storage-system row 800px tall.
 */
class RoadmapEdgeCellTest {

    private val user = TokenProfile(
        id = 1,
        uuid = "uuid",
        minecraftUsername = "player",
        displayName = "Player",
        roles = emptyList(),
    )

    private fun node(
        id: Int,
        name: String,
        state: ProjectState = ProjectState.ACTIVE,
        layer: Int = 0,
        isBlocked: Boolean = false,
    ) = RoadmapNode(
        projectId = id,
        projectName = name,
        projectType = ProjectType.BUILDING,
        stage = ProjectStage.RESOURCE_GATHERING,
        state = state,
        tasksTotal = 0,
        tasksCompleted = 0,
        isBlocked = isBlocked,
        blockingProjectIds = emptyList(),
        dependentProjectIds = emptyList(),
        layer = layer,
    )

    /** `consumer` depends on `producer` for [itemName]. */
    private fun edge(
        consumerId: Int,
        consumerName: String,
        producerId: Int,
        producerName: String,
        itemName: String?,
        quantity: Long?,
        isBlocking: Boolean = false,
    ) = RoadmapEdge(
        fromNodeId = consumerId,
        fromNodeName = consumerName,
        toNodeId = producerId,
        toNodeName = producerName,
        isBlocking = isBlocking,
        itemName = itemName,
        quantity = quantity,
    )

    private fun roadmap(nodes: List<RoadmapNode>, edges: List<RoadmapEdge>) = Roadmap(
        worldId = 1,
        worldName = "Forever world",
        nodes = nodes,
        edges = edges,
        layers = listOf(RoadmapLayer(depth = 0, projectIds = nodes.map { it.projectId }, projectCount = nodes.size)),
    )

    private fun render(nodes: List<RoadmapNode>, edges: List<RoadmapEdge>) =
        roadmapPage(user = user, roadmap = roadmap(nodes, edges))

    @Test
    fun `many resources from one producer collapse to a single line naming the largest`() {
        val storage = node(1, "Storage System YAMS")
        val hall = node(2, "End Trading Hall", state = ProjectState.DONE)
        val terracotta = listOf("Black Terracotta", "Blue Terracotta", "Cyan Terracotta")
        val edges = terracotta.map { edge(1, storage.projectName, 2, hall.projectName, it, 1L) } +
            edge(1, storage.projectName, 2, hall.projectName, "Block of Quartz", 1307L)

        val html = render(listOf(storage, hall), edges)

        // The headline is the largest demand, not the alphabetically first.
        assertContains(html, "1,307 Block of Quartz")
        assertContains(html, "+3 more")
        // The other three are counted, never named — naming them is what blew the cell up.
        terracotta.forEach { assertFalse(html.contains(it), "$it should be counted, not named") }
    }

    @Test
    fun `one producer produces one line however many edges it has`() {
        val storage = node(1, "Storage System YAMS")
        val hall = node(2, "End Trading Hall", state = ProjectState.DONE)
        val edges = (1..40).map {
            edge(1, storage.projectName, 2, hall.projectName, "Terracotta $it", 1L)
        }

        val html = render(listOf(storage, hall), edges)

        assertEquals(
            1,
            Regex("End Trading Hall").findAll(html).count() - 1,
            "the producer should be named once in the cell, plus once in its own row",
        )
    }

    @Test
    fun `a blocker is never folded away, however many supplying projects there are`() {
        val storage = node(1, "Storage System YAMS", isBlocked = true)
        val ghast = node(2, "Ghast Farm")
        val suppliers = (3..9).map { node(it, "Farm $it", state = ProjectState.DONE) }
        val edges = listOf(
            edge(1, storage.projectName, 2, ghast.projectName, "Gunpowder", 5L, isBlocking = true),
        ) + suppliers.map { edge(1, storage.projectName, it.projectId, it.projectName, "Cobblestone", 100L) }

        val html = render(listOf(storage, ghast) + suppliers, edges)

        assertContains(html, "7 projects supplying")
        assertContains(html, "blocking")
        // The blocker renders above the disclosure, so it survives the fold being shut.
        assertTrue(
            html.indexOf("roadmap-edge--blocking") < html.indexOf("roadmap-edge-fold"),
            "a blocker must render before the fold, not inside it",
        )
    }

    @Test
    fun `a handful of suppliers still renders flat`() {
        val storage = node(1, "Storage System YAMS")
        val suppliers = (2..4).map { node(it, "Farm $it", state = ProjectState.DONE) }
        val edges = suppliers.map { edge(1, storage.projectName, it.projectId, it.projectName, "Cobblestone", 100L) }

        val html = render(listOf(storage) + suppliers, edges)

        assertFalse(html.contains("roadmap-edge-fold"), "a small world should never meet a disclosure")
        suppliers.forEach { assertContains(html, it.projectName) }
    }

    @Test
    fun `a producer supplying one resource prints no more-count`() {
        val storage = node(1, "Storage System YAMS")
        val cobble = node(2, "Cobble farm", state = ProjectState.DONE)

        val html = render(
            listOf(storage, cobble),
            listOf(edge(1, storage.projectName, 2, cobble.projectName, "Cobblestone", 74557L)),
        )

        assertContains(html, "74,557 Cobblestone")
        assertFalse(
            html.contains("roadmap-edge__more"),
            "a single-resource pair has nothing to count",
        )
    }

    @Test
    fun `a manual sequencing edge never out-ranks a resource it shares a pair with`() {
        val storage = node(1, "Storage System YAMS")
        val hall = node(2, "End Trading Hall", state = ProjectState.DONE)

        val html = render(
            listOf(storage, hall),
            listOf(
                // A declared project→project edge carries no item and no quantity by design.
                edge(1, storage.projectName, 2, hall.projectName, itemName = null, quantity = null),
                edge(1, storage.projectName, 2, hall.projectName, "Block of Quartz", 1307L),
            ),
        )

        assertContains(html, "1,307 Block of Quartz")
        assertTrue(html.contains("supplying"), "a pair with a resource edge is supplying, not done")
    }
}
