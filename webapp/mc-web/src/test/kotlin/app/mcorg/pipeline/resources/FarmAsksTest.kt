package app.mcorg.pipeline.resources

import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.project.ProjectState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the world's plans ask of a farm (MCO-605), with Forever world's Tree Farm and the two plans
 * of design frame 2a·C: Storage System YAMS and Copper Library.
 */
class FarmAsksTest {

    private val tree = 45
    private val yams = 43
    private val copper = 44

    private fun rate(itemId: String, perHour: Int, mode: String, modeId: Int, position: Int) =
        FarmModeRate(tree, modeId, mode, position, itemId, perHour)

    private val rates = listOf(
        rate("minecraft:oak_log", 48_000, "Oak Mode", 11, 0),
        rate("minecraft:birch_log", 57_700, "Birch Mode", 12, 1),
        rate("minecraft:spruce_log", 37_600, "Spruce Mode", 13, 2),
        rate("minecraft:oak_log", 38_100, "Azalea Mode", 17, 6),
    )

    private fun edge(consumer: Int, name: String, itemId: String, quantity: Long, state: ProjectState = ProjectState.ACTIVE) =
        ProjectResourceEdge(
            consumerId = consumer,
            consumerName = name,
            consumerState = state,
            producerId = tree,
            producerName = "Tree Farm",
            itemName = itemId.substringAfter(':'),
            producerState = ProjectState.DONE,
            quantity = quantity,
            itemId = itemId,
        )

    private val edges = listOf(
        edge(yams, "Storage System YAMS", "minecraft:spruce_log", 28_191),
        edge(yams, "Storage System YAMS", "minecraft:oak_log", 57),
        edge(yams, "Storage System YAMS", "minecraft:birch_log", 16),
        edge(copper, "Copper Library", "minecraft:spruce_log", 6_055),
        edge(copper, "Copper Library", "minecraft:oak_log", 54),
    )

    @Test
    fun `a mode's ask is summed over the plans, and its time comes from the sum`() {
        val asks = FarmAsks.of(tree, "Tree Farm", edges, emptyMap(), rates)

        val spruce = asks.asked.single { it.modeName == "Spruce Mode" }
        val logs = spruce.items.single()
        assertEquals(34_246, logs.left)
        assertEquals(listOf("Storage System YAMS" to 28_191L, "Copper Library" to 6_055L), logs.asks.map { it.projectName to it.left })
        assertEquals(55.0, assertNotNull(spruce.hoursLeft) * 60, 0.5)
        assertEquals(setOf(yams, copper), spruce.planIds)
    }

    @Test
    fun `an item goes to the faster of two modes, and the slower is not asked for`() {
        val asks = FarmAsks.of(tree, "Tree Farm", edges, emptyMap(), rates)

        val oak = asks.asked.single { it.modeName == "Oak Mode" }
        assertEquals(111, oak.items.single().left)
        assertTrue(oak.isToken, "111 Oak Log at 48,000/hr is seconds")
        assertTrue(asks.asked.none { it.modeName == "Azalea Mode" })
        assertEquals(4, asks.farmModeCount)
    }

    @Test
    fun `what a plan has logged is not asked again`() {
        val asks = FarmAsks.of(
            tree, "Tree Farm", edges,
            progress = mapOf(yams to mapOf("minecraft:spruce_log" to 28_191)),
            rates = rates,
        )

        val spruce = asks.asked.single { it.modeName == "Spruce Mode" }
        assertEquals(listOf("Copper Library" to 6_055L), spruce.items.single().asks.map { it.projectName to it.left })
        assertEquals(10.0, assertNotNull(spruce.hoursLeft) * 60, 0.5)
    }

    @Test
    fun `a finished plan asks nothing, whatever its cached demand says`() {
        val done = edges.map { if (it.consumerId == yams) it.copy(consumerState = ProjectState.DONE) else it }

        val asks = FarmAsks.of(tree, "Tree Farm", done, emptyMap(), rates)

        assertEquals(setOf(copper), asks.asked.flatMap { it.planIds }.toSet())
    }

    @Test
    fun `no plan asking leaves nothing to say`() {
        assertTrue(FarmAsks.of(tree, "Tree Farm", emptyList(), emptyMap(), rates).isEmpty)
    }
}
