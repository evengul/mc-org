package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.project.ProjectState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-460 — loops in the derived dependency graph.
 *
 * The shape throughout is the one Even hit on the dogfood world: the cobblestone farm needs
 * gunpowder for TNT, the witch farm produces gunpowder, the witch farm needs cobblestone, the
 * cobblestone farm produces cobblestone. Both edges true, neither one a mistake.
 */
class RoadmapCyclesTest {

    private val cobbleFarm = 1
    private val witchFarm = 2
    private val storageHall = 3
    private val perimeter = 4

    /** The world default (MCO-401). Every fixture claim above is comfortably over it. */
    private val threshold = 1_728

    private fun detect(edges: List<ProjectResourceEdge>) = RoadmapCycles.detect(edges, threshold)

    private fun edge(
        consumerId: Int,
        consumerName: String,
        producerId: Int,
        producerName: String,
        itemName: String? = "Cobblestone",
        quantity: Long? = 1_000,
        producerState: ProjectState = ProjectState.ACTIVE,
    ) = ProjectResourceEdge(
        consumerId = consumerId,
        consumerName = consumerName,
        consumerState = ProjectState.ACTIVE,
        producerId = producerId,
        producerName = producerName,
        itemName = itemName,
        producerState = producerState,
        quantity = quantity,
    )

    /** Cobble needs 2,400 gunpowder from Witch; Witch needs 75,151 cobblestone from Cobble. */
    private fun theLoop() = listOf(
        edge(cobbleFarm, "Cobblestone Farm", witchFarm, "Witch Hut Farm", "Gunpowder", 2_400),
        edge(witchFarm, "Witch Hut Farm", cobbleFarm, "Cobblestone Farm", "Cobblestone", 75_151),
    )

    // ---- detection -------------------------------------------------------------------

    @Test
    fun `two farms supplying each other are one cycle`() {
        val cycles = detect(theLoop())

        assertEquals(1, cycles.size)
        assertEquals(listOf(cobbleFarm, witchFarm), cycles.single().projectIds.sorted())
    }

    @Test
    fun `an ordinary chain is not a cycle`() {
        val chain = listOf(
            edge(storageHall, "Storage Hall", cobbleFarm, "Cobblestone Farm"),
            edge(cobbleFarm, "Cobblestone Farm", perimeter, "Perimeter"),
        )

        assertTrue(detect(chain).isEmpty())
    }

    @Test
    fun `no edges means no cycles`() {
        assertTrue(detect(emptyList()).isEmpty())
    }

    @Test
    fun `a project downstream of a cycle is not named as part of it`() {
        // The point of detecting rather than inferring. Storage Hall depends on the cobble
        // farm, so the old "whatever BFS could not place" reading swept it in with the loop.
        val edges = theLoop() + edge(storageHall, "Storage Hall", cobbleFarm, "Cobblestone Farm")

        val cycle = detect(edges).single()

        assertEquals(listOf(cobbleFarm, witchFarm), cycle.projectIds.sorted())
        assertTrue(storageHall !in cycle.projectIds, "it merely depends on a cycle member")
    }

    @Test
    fun `a three-farm loop is one cycle, not three`() {
        val edges = listOf(
            edge(1, "A", 2, "B", "Iron", 5_000),
            edge(2, "B", 3, "C", "Redstone", 6_000),
            edge(3, "C", 1, "A", "Cobblestone", 7_000),
        )

        val cycle = detect(edges).single()

        assertEquals(listOf(1, 2, 3), cycle.projectIds.sorted())
        assertEquals(3, cycle.options.size, "any of the three edges would break it")
    }

    @Test
    fun `two separate loops are reported separately`() {
        val edges = theLoop() + listOf(
            edge(10, "Gold Farm", 11, "Ice Farm", "Ice", 9_000),
            edge(11, "Ice Farm", 10, "Gold Farm", "Gold", 8_000),
        )

        assertEquals(2, detect(edges).size)
    }

    // ---- the guess -------------------------------------------------------------------

    @Test
    fun `the loop breaks at its smallest claim`() {
        val cycle = detect(theLoop()).single()

        // 2,400 gunpowder is the cheaper of the two to do by hand, so the cobblestone farm
        // stops waiting on the witch farm and comes first.
        assertEquals(cobbleFarm, cycle.breaking.firstProjectId)
        assertEquals(witchFarm, cycle.breaking.waitingProjectId)
        assertEquals("Gunpowder", cycle.breaking.itemName)
        assertEquals(2_400, cycle.breaking.quantity)
    }

    @Test
    fun `both directions are offered so the guess can be overridden`() {
        val cycle = detect(theLoop()).single()

        assertEquals(
            listOf(cobbleFarm to witchFarm, witchFarm to cobbleFarm),
            cycle.options.map { it.firstProjectId to it.waitingProjectId }.sortedBy { it.first },
        )
    }

    @Test
    fun `the guess does not move when the edges arrive in another order`() {
        val forward = detect(theLoop()).single()
        val reversed = detect(theLoop().reversed()).single()

        assertEquals(forward.breaking, reversed.breaking, "a reload must not reshuffle the page")
    }

    @Test
    fun `equal claims break on project id rather than arbitrarily`() {
        val tied = listOf(
            edge(witchFarm, "Witch Hut Farm", cobbleFarm, "Cobblestone Farm", "Cobblestone", 5_000),
            edge(cobbleFarm, "Cobblestone Farm", witchFarm, "Witch Hut Farm", "Gunpowder", 5_000),
        )

        val cycle = detect(tied).single()

        assertEquals(cobbleFarm, cycle.breaking.firstProjectId, "lowest id wins, deterministically")
    }

    @Test
    fun `a declared dependency is never the guess while a derived edge will do`() {
        // A null quantity is a project_dependencies row — a person wrote "dig the perimeter
        // first". Breaking the loop there would override their decision to tidy up a loop
        // they did not create, even though it looks like the smallest claim.
        val edges = listOf(
            edge(cobbleFarm, "Cobblestone Farm", perimeter, "Perimeter", itemName = null, quantity = null),
            edge(perimeter, "Perimeter", cobbleFarm, "Cobblestone Farm", "Cobblestone", 40_000),
        )

        val cycle = detect(edges).single()

        assertEquals(perimeter, cycle.breaking.firstProjectId, "the derived edge is the one to set aside")
        assertEquals(40_000, cycle.breaking.quantity)
    }

    // ---- the threshold settles the lopsided ones (MCO-460) --------------------------

    @Test
    fun `a loop resting on a footnote is broken without asking`() {
        // Even's actual case: 20 gunpowder against 75,151 cobblestone. Both edges true, but
        // one is an evening's hand-gathering and the other is the largest job in the world.
        val lopsided = listOf(
            edge(cobbleFarm, "Cobblestone Farm", witchFarm, "Witch Hut Farm", "Gunpowder", 20),
            edge(witchFarm, "Witch Hut Farm", cobbleFarm, "Cobblestone Farm", "Cobblestone", 75_151),
        )

        val cycle = detect(lopsided).single()

        assertTrue(!cycle.needsAnAnswer, "the threshold answered it; nobody needs interrupting")
        assertEquals(cobbleFarm, cycle.breaking.firstProjectId, "but it is still broken")
    }

    @Test
    fun `a balanced loop is still a question`() {
        val cycle = detect(theLoop()).single()

        assertTrue(cycle.needsAnAnswer, "2,400 gunpowder is farm-scale; no principle picks a winner")
    }

    @Test
    fun `a footnote losing to a declared dependency needs no answer`() {
        // Someone said "dig the perimeter first"; the perimeter happens to want 40 cobblestone
        // back. The two rules compose into the obvious outcome without anyone being asked: the
        // footnote is the one set aside, so the human's declared order survives intact.
        val edges = listOf(
            edge(cobbleFarm, "Cobblestone Farm", perimeter, "Perimeter", itemName = null, quantity = null),
            edge(perimeter, "Perimeter", cobbleFarm, "Cobblestone Farm", "Cobblestone", 40),
        )

        val cycle = detect(edges).single()

        assertEquals(perimeter, cycle.breaking.firstProjectId, "the 40 cobblestone gives way")
        assertTrue(!cycle.needsAnAnswer, "and the declared dependency is honoured, silently")
    }

    @Test
    fun `a declared dependency against a farm-scale claim is a question`() {
        // Now the derived side is real work, so setting either aside is a judgement call and
        // the declared row is the one the rules refuse to overrule on their own.
        val edges = listOf(
            edge(cobbleFarm, "Cobblestone Farm", perimeter, "Perimeter", itemName = null, quantity = null),
            edge(perimeter, "Perimeter", cobbleFarm, "Cobblestone Farm", "Cobblestone", 40_000),
        )

        val cycle = detect(edges).single()

        assertEquals(perimeter, cycle.breaking.firstProjectId, "still never the declared row")
        assertTrue(cycle.needsAnAnswer, "but 40,000 cobblestone is not something to drop quietly")
    }

    @Test
    fun `a loop of only declared dependencies still breaks somewhere`() {
        // Nothing else is available, so the rule yields rather than leaving the page
        // contradicting itself.
        val edges = listOf(
            edge(1, "A", 2, "B", itemName = null, quantity = null),
            edge(2, "B", 1, "A", itemName = null, quantity = null),
        )

        val cycle = detect(edges).single()

        assertEquals(1, cycle.breaking.firstProjectId)
    }

    @Test
    fun `several items between the same pair are one dependency`() {
        // Otherwise the witch farm needing cobblestone, sand and gravel would offer three
        // identical "witch farm comes first" options.
        val edges = listOf(
            edge(cobbleFarm, "Cobblestone Farm", witchFarm, "Witch Hut Farm", "Gunpowder", 2_400),
            edge(witchFarm, "Witch Hut Farm", cobbleFarm, "Cobblestone Farm", "Cobblestone", 75_151),
            edge(witchFarm, "Witch Hut Farm", cobbleFarm, "Cobblestone Farm", "Gravel", 12_000),
        )

        val cycle = detect(edges).single()

        assertEquals(2, cycle.options.size)
        assertEquals(
            75_151,
            cycle.options.single { it.firstProjectId == witchFarm }.quantity,
            "the largest claim represents the pair",
        )
    }

    @Test
    fun `a self-supplying project is not a cycle`() {
        // Excluded upstream in SQL, but the detector must not invent one either.
        val edges = listOf(edge(cobbleFarm, "Cobblestone Farm", cobbleFarm, "Cobblestone Farm"))

        assertTrue(detect(edges).isEmpty())
    }
}
