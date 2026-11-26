package app.mcorg.pipeline.resources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The order and folds of a plan's Collect section (MCO-604), with Forever world's farms and the
 * times MCO-603's fixture table gives them.
 */
class CollectBlocksTest {

    private var nextId = 1

    /** A single-mode farm with one line: [quantity] at [ratePerHour] takes the time it takes. */
    private fun farm(name: String, quantity: Long, ratePerHour: Int, logged: Long = 0): FarmRun {
        val line = SuppliedLine("minecraft:${name.lowercase().replace(' ', '_')}", name, quantity, logged)
        return FarmRun(
            projectId = nextId++,
            projectName = name,
            modes = listOf(ModeRun(modeId = null, modeName = null, modePosition = 0, items = listOf(ItemRun(line, ratePerHour)))),
        )
    }

    /** A farm that runs [hours] for its one line, nothing logged. */
    private fun farmOf(name: String, hours: Double) = farm(name, quantity = (hours * 1_000).toLong(), ratePerHour = 1_000)

    private fun names(runs: List<FarmRun>) = runs.map { it.projectName }

    // YAMS's Collect section: ten running farms (MCO-603's fixture table and frame 2a·A).
    private val yams = listOf(
        farm("Bartering setup", 296_800, 1_000),
        farm("Skeleton spawner farm", 455, 20),
        farm("Witch hut farm", 20_573, 1_580),
        farm("Tree Farm", 28_191, 37_600),
        farmOf("Melon & Pumpkin farm", 18.5),
        farmOf("End Trading Hall", 16.1),
        farmOf("Moss farm", 14.4),
        farmOf("Nanofarm", 13.4),
        farmOf("Guardian farm", 9.1),
        farmOf("Nether Fortress farm", 49.0 / 60),
        farmOf("Flower farm", 29.0 / 60),
        farmOf("Small-ish netherwart farm", 25.0 / 60),
        farmOf("Cactus farm", 17.0 / 60),
        farmOf("Shulker farm", 0.5 / 60),
    )

    @Test
    fun `farms are ordered by the time they run, longest first, not by their biggest line`() {
        val blocks = CollectBlocks.of(yams)

        assertEquals(
            listOf(
                "Bartering setup", "Skeleton spawner farm", "Melon & Pumpkin farm", "End Trading Hall",
                "Moss farm", "Nanofarm", "Witch hut farm", "Guardian farm", "Nether Fortress farm", "Tree Farm",
            ),
            names(blocks.shown),
            "455 Bow puts the skeleton farm second, ahead of Witch hut farm's 63,273 Redstone",
        )
    }

    @Test
    fun `a section of more than six farms folds the ones under thirty minutes`() {
        val blocks = CollectBlocks.of(yams)

        assertEquals(
            listOf("Flower farm", "Small-ish netherwart farm", "Cactus farm", "Shulker farm"),
            names(blocks.folded),
        )
        assertTrue(blocks.shown.all { it.hoursLeft!! >= CollectBlocks.FOLD_BELOW_HOURS })
    }

    @Test
    fun `six farms or fewer fold nothing, however short they run`() {
        // Copper Library: five farms, the last of them ~10 minutes (frame 2a·B).
        val copper = listOf(
            farmOf("Bartering setup", 9.1),
            farmOf("Wither rose farm", 1.4),
            farmOf("Witch hut farm", 29.0 / 60),
            farmOf("Small-ish netherwart farm", 22.0 / 60),
            farm("Tree Farm", 6_055, 37_600),
        )

        val blocks = CollectBlocks.of(copper)

        assertEquals(5, blocks.shown.size)
        assertTrue(blocks.folded.isEmpty())
    }

    @Test
    fun `a farm with a line in progress never folds, though what is left is under thirty minutes`() {
        // Frame 2a·E: 20,000 of 28,191 Spruce Log logged leaves ~13 minutes of Tree Farm.
        val inProgress = yams.map { if (it.projectName == "Tree Farm") farm("Tree Farm", 28_191, 37_600, logged = 20_000) else it }

        val blocks = CollectBlocks.of(inProgress)

        val tree = blocks.shown.single { it.projectName == "Tree Farm" }
        assertEquals(13.0, tree.hoursLeft!! * 60, 0.5)
        assertTrue(blocks.folded.none { it.projectName == "Tree Farm" })
    }

    @Test
    fun `a farm with every line logged leaves the order for the done fold`() {
        // Frame 2a·F: Copper Library's Witch hut farm, Stick 599 and Glass Bottle 384 ticked.
        val witch = FarmRun(
            projectId = 21,
            projectName = "Witch hut farm",
            modes = listOf(
                ModeRun(
                    null, null, 0,
                    listOf(
                        ItemRun(SuppliedLine("minecraft:stick", "Stick", 599, logged = 599), 1_580),
                        ItemRun(SuppliedLine("minecraft:glass_bottle", "Glass Bottle", 384, logged = 384), 785),
                    ),
                ),
            ),
        )

        val blocks = CollectBlocks.of(listOf(farmOf("Bartering setup", 9.1), witch))

        assertEquals(listOf("Bartering setup"), names(blocks.shown))
        assertEquals(listOf("Witch hut farm"), names(blocks.done))
    }

    @Test
    fun `a farm with no measured rate goes after the timed farms and never folds`() {
        // Frame 2a·G: a bee farm whose Honeycomb rate is 0 still supplies; it only has no time.
        val bees = farm("Bee farm", 340, 0)

        val blocks = CollectBlocks.of(yams + bees)

        assertEquals("Bee farm", blocks.shown.last().projectName)
        assertTrue(blocks.folded.none { it.projectName == "Bee farm" })
    }
}
