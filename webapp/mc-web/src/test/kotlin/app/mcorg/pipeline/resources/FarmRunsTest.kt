package app.mcorg.pipeline.resources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which mode a farm runs in, and for how long, to feed one plan (MCO-603).
 *
 * The figures are Forever world's, the world design frame 2a was drawn on: the Tree Farm's seven
 * modes at the rates of idea 14, and the Witch hut farm's measured rates.
 */
class FarmRunsTest {

    private val treeFarm = 45
    private val witchHut = 21
    private val beeFarm = 60

    private fun producer(itemId: String, projectId: Int, name: String) =
        itemId to FarmSupplyRow(itemId = itemId, projectId = projectId, projectName = name)

    private fun rate(projectId: Int, itemId: String, perHour: Int, mode: String? = null, modeId: Int? = null, position: Int = 0) =
        FarmModeRate(
            projectId = projectId,
            modeId = modeId,
            modeName = mode,
            modePosition = position,
            itemId = itemId,
            ratePerHour = perHour,
        )

    private fun line(itemId: String, quantity: Long, logged: Long = 0) =
        SuppliedLine(itemId = itemId, itemName = itemId.substringAfter(':'), quantity = quantity, logged = logged)

    private val treeRates = listOf(
        rate(treeFarm, "minecraft:oak_log", 48_000, "Oak Mode", 11, 0),
        rate(treeFarm, "minecraft:birch_log", 57_700, "Birch Mode", 12, 1),
        rate(treeFarm, "minecraft:spruce_log", 37_600, "Spruce Mode", 13, 2),
        rate(treeFarm, "minecraft:jungle_log", 46_300, "Jungle Mode", 14, 3),
        rate(treeFarm, "minecraft:acacia_log", 45_200, "Acacia Mode", 15, 4),
        rate(treeFarm, "minecraft:cherry_log", 71_700, "Cherry Mode", 16, 5),
        rate(treeFarm, "minecraft:oak_log", 38_100, "Azalea Mode", 17, 6),
    )

    private val treeProducers = listOf(
        "minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log",
        "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:cherry_log",
    ).associate { producer(it, treeFarm, "Tree Farm") }

    private val witchRates = listOf(
        rate(witchHut, "minecraft:redstone", 8_280),
        rate(witchHut, "minecraft:stick", 1_580),
        rate(witchHut, "minecraft:glass_bottle", 785),
        rate(witchHut, "minecraft:glowstone_dust", 785),
        rate(witchHut, "minecraft:gunpowder", 785),
        rate(witchHut, "minecraft:sugar", 785),
    )

    private val witchProducers = witchRates.associate { producer(it.itemId, witchHut, "Witch hut farm") }

    private val yamsTreeLines = listOf(
        line("minecraft:spruce_log", 28_191),
        line("minecraft:oak_log", 57),
        line("minecraft:acacia_log", 16),
        line("minecraft:birch_log", 16),
        line("minecraft:cherry_log", 16),
        line("minecraft:jungle_log", 16),
    )

    private fun minutes(hours: Double?) = assertNotNull(hours) * 60

    @Test
    fun `YAMS asks the Tree Farm for six of its seven modes, one of which takes real time`() {
        val run = FarmRuns.derive(yamsTreeLines, treeProducers, treeRates).single()

        assertEquals("Tree Farm", run.projectName)
        assertEquals(
            listOf("Oak Mode", "Birch Mode", "Spruce Mode", "Jungle Mode", "Acacia Mode", "Cherry Mode"),
            run.modes.map { it.modeName },
            "every mode asked for, in the farm's own order; Azalea Mode is not asked for",
        )
        val spruce = run.modes.single { it.modeName == "Spruce Mode" }
        assertEquals(45.0, minutes(spruce.hoursLeft), 0.1)
        assertFalse(spruce.isToken)
        assertEquals(5, run.modes.count { it.isToken }, "Oak, Birch, Jungle, Acacia and Cherry run seconds each")
        assertEquals("minecraft:spruce_log", run.setBy?.line?.itemId)
    }

    @Test
    fun `an item two modes make goes to the faster mode`() {
        // Oak Log comes from Oak Mode at 48,000/hr and from Azalea Mode at 38,100/hr.
        val run = FarmRuns.derive(listOf(line("minecraft:oak_log", 57)), treeProducers, treeRates).single()

        assertEquals("Oak Mode", run.modes.single().modeName)
        assertEquals(48_000, run.modes.single().items.single().ratePerHour)
    }

    @Test
    fun `the faster mode wins wherever the farm lists it`() {
        // In the Tree Farm the faster mode also comes first, which a pick by position would pass too.
        val rates = listOf(
            rate(treeFarm, "minecraft:oak_log", 38_100, "Azalea Mode", 17, 0),
            rate(treeFarm, "minecraft:oak_log", 48_000, "Oak Mode", 11, 1),
        )

        val run = FarmRuns.derive(listOf(line("minecraft:oak_log", 57)), treeProducers, rates).single()

        assertEquals("Oak Mode", run.modes.single().modeName)
    }

    // A tree farm whose modes all make sticks too, as many designs' do.
    private val stickRates = listOf(
        rate(treeFarm, "minecraft:oak_log", 48_000, "Oak Mode", 11, 0),
        rate(treeFarm, "minecraft:stick", 1_500, "Oak Mode", 11, 0),
        rate(treeFarm, "minecraft:jungle_log", 46_300, "Jungle Mode", 14, 1),
        rate(treeFarm, "minecraft:stick", 1_580, "Jungle Mode", 14, 1),
    )
    private val stickProducers = treeProducers + producer("minecraft:stick", treeFarm, "Tree Farm")

    @Test
    fun `an item rides along on a mode the plan already runs, rather than starting a faster one`() {
        // Decided 2026-10-09: 1,000 Stick come out of the hour of Oak Mode the Oak Log needs anyway;
        // switching to Jungle Mode for them, at 1,580/hr, would add 38 minutes for nothing.
        val lines = listOf(line("minecraft:oak_log", 48_000), line("minecraft:stick", 1_000))

        val run = FarmRuns.derive(lines, stickProducers, stickRates).single()

        assertEquals(listOf("Oak Mode"), run.modes.map { it.modeName })
        assertEquals(1.0, assertNotNull(run.hoursLeft), 0.001)
    }

    @Test
    fun `an item starts its own faster mode when riding along would take longer`() {
        // Oak Mode is already chosen for the hour of Oak Log. At 100/hr there, 1,000 Stick would
        // stretch it to 10 hours; Jungle Mode makes them in 38 minutes.
        val rates = listOf(
            rate(treeFarm, "minecraft:oak_log", 48_000, "Oak Mode", 11, 0),
            rate(treeFarm, "minecraft:stick", 100, "Oak Mode", 11, 0),
            rate(treeFarm, "minecraft:stick", 1_580, "Jungle Mode", 14, 1),
        )
        val lines = listOf(line("minecraft:oak_log", 48_000), line("minecraft:stick", 1_000))

        val run = FarmRuns.derive(lines, stickProducers, rates).single()

        assertEquals(listOf("Oak Mode", "Jungle Mode"), run.modes.map { it.modeName })
        assertEquals(1.0 + 1_000.0 / 1_580, assertNotNull(run.hoursLeft), 0.001)
    }

    @Test
    fun `items one mode makes share its time, the longer one, and the item that sets it is named`() {
        val lines = listOf(
            line("minecraft:redstone", 63_273),
            line("minecraft:stick", 20_573),
            line("minecraft:glass_bottle", 216),
            line("minecraft:glowstone_dust", 124),
            line("minecraft:gunpowder", 5),
            line("minecraft:sugar", 2),
        )

        val run = FarmRuns.derive(lines, witchProducers, witchRates).single()

        assertEquals(13.0, assertNotNull(run.hoursLeft), 0.05, "Stick's 13.0 h, not the sum of six items")
        assertEquals("minecraft:stick", run.setBy?.line?.itemId)
        assertEquals(1, run.modes.size, "a farm without modes is one implicit mode")
        assertNull(run.modes.single().modeId)
    }

    @Test
    fun `the item that sets the time is not always the biggest line`() {
        // Copper Library: 599 Stick at 1,580/hr is ~23 min, 384 Glass Bottle at 785/hr is ~29 min.
        val lines = listOf(line("minecraft:stick", 599), line("minecraft:glass_bottle", 384))

        val run = FarmRuns.derive(lines, witchProducers, witchRates).single()

        assertEquals("minecraft:glass_bottle", run.setBy?.line?.itemId)
        assertEquals(29.0, minutes(run.hoursLeft), 0.5)
    }

    @Test
    fun `the time counts down as the line is logged, and remembers the whole run`() {
        val lines = listOf(line("minecraft:spruce_log", 28_191, logged = 20_000))

        val run = FarmRuns.derive(lines, treeProducers, treeRates).single()

        assertEquals(13.0, minutes(run.hoursLeft), 0.1, "8,191 left at 37,600/hr")
        assertEquals(45.0, minutes(run.hoursNeeded), 0.1, "the whole need, for \"~13 min left of ~45 min\"")
    }

    @Test
    fun `when the line that sets the time is done, the next one sets it`() {
        val lines = listOf(line("minecraft:stick", 599), line("minecraft:glass_bottle", 384, logged = 384))

        val run = FarmRuns.derive(lines, witchProducers, witchRates).single()

        assertEquals("minecraft:stick", run.setBy?.line?.itemId)
        assertEquals(23.0, minutes(run.hoursLeft), 0.5)
        assertFalse(run.isDone)
    }

    @Test
    fun `a farm whose lines are all done has no time and is done`() {
        val lines = listOf(line("minecraft:stick", 599, logged = 599), line("minecraft:glass_bottle", 384, logged = 400))

        val run = FarmRuns.derive(lines, witchProducers, witchRates).single()

        assertTrue(run.isDone)
        assertNull(run.setBy)
        assertNull(run.hoursLeft)
    }

    @Test
    fun `a farm runs one mode at a time, so its modes' times add up`() {
        // Not drawn in frame 2a, which never shows two modes that both take real time.
        val lines = listOf(line("minecraft:spruce_log", 37_600), line("minecraft:cherry_log", 35_850))

        val run = FarmRuns.derive(lines, treeProducers, treeRates).single()

        assertEquals(1.5, assertNotNull(run.hoursLeft), 0.001, "1 h of Spruce Mode, then half an hour of Cherry Mode")
        assertEquals("minecraft:spruce_log", run.setBy?.line?.itemId, "the longest mode's item sets the farm")
    }

    @Test
    fun `an unmeasured rate gives no time, and the line still counts as the farm's`() {
        val lines = listOf(line("minecraft:honeycomb", 340))
        val producers = mapOf(producer("minecraft:honeycomb", beeFarm, "Bee farm"))
        val rates = listOf(rate(beeFarm, "minecraft:honeycomb", 0))

        val run = FarmRuns.derive(lines, producers, rates).single()

        assertNull(run.hoursLeft)
        assertNull(run.setBy)
        assertFalse(run.isDone)
        assertEquals(listOf("minecraft:honeycomb"), run.unmeasured.map { it.line.itemId })
    }

    @Test
    fun `a measured item gives the farm a time although another item is unmeasured`() {
        val lines = listOf(line("minecraft:stick", 599), line("minecraft:spider_eye", 40))
        val rates = witchRates + rate(witchHut, "minecraft:spider_eye", 0)
        val producers = witchProducers + producer("minecraft:spider_eye", witchHut, "Witch hut farm")

        val run = FarmRuns.derive(lines, producers, rates).single()

        assertEquals("minecraft:stick", run.setBy?.line?.itemId)
        assertEquals(listOf("minecraft:spider_eye"), run.unmeasured.map { it.line.itemId })
    }

    @Test
    fun `lines no farm supplies are left out`() {
        val lines = yamsTreeLines + line("minecraft:dark_oak_log", 1_779)

        val runs = FarmRuns.derive(lines, treeProducers, treeRates)

        assertTrue(runs.flatMap { run -> run.modes.flatMap { it.items } }.none { it.line.itemId == "minecraft:dark_oak_log" })
    }

    @Test
    fun `each farm is its own run`() {
        val lines = yamsTreeLines + line("minecraft:stick", 20_573)

        val runs = FarmRuns.derive(lines, treeProducers + witchProducers, treeRates + witchRates)

        assertEquals(setOf("Tree Farm", "Witch hut farm"), runs.map { it.projectName }.toSet())
    }
}
