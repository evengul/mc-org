package app.mcorg.pipeline.project

import app.mcorg.domain.model.idea.IdeaProductionMode
import app.mcorg.domain.model.idea.bestRateFor
import app.mcorg.domain.model.idea.produces
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-412 — which mode's rates answer a question.
 *
 * Two different questions, two different answers, and they must not be confused:
 *
 * - *"Can this farm cover my demand?"* — [bestRateFor], the best any mode achieves, named.
 * - *"What does the project I just imported produce?"* — [ratesForImport], one mode's whole set,
 *   because a farm in a world runs one way at a time.
 */
class IdeaProductionModeSelectionTest {

    private fun mode(name: String, position: Int, vararg rates: Pair<String, Int?>) =
        IdeaProductionMode(id = position + 1, name = name, position = position, rates = rates.toMap())

    private val iceFarm = listOf(
        mode("Max speed", 0, "minecraft:ice" to 62_000),
        mode("Slowed", 1, "minecraft:ice" to 18_000),
    )

    // A nether fortress farm: a wither-skeleton filter crossed with three speeds, modelled flat
    // as six modes rather than two axes.
    private val fortressFarm = listOf(
        mode("Skeletons only, fast", 0, "minecraft:bone" to 900, "minecraft:wither_skeleton_skull" to 40),
        mode("Skeletons only, slow", 1, "minecraft:bone" to 300, "minecraft:wither_skeleton_skull" to 12),
        mode("Everything, fast", 2, "minecraft:bone" to 700, "minecraft:blaze_rod" to 500, "minecraft:nether_wart" to 200),
    )

    @Test
    fun `the best rate across modes is the one a suggestion should quote`() {
        val (mode, rate) = iceFarm.bestRateFor("minecraft:ice")!!

        assertEquals(62_000, rate)
        assertEquals("Max speed", mode.name)
    }

    @Test
    fun `the best rate names its own mode so the assumption is visible`() {
        // The point of returning the mode alongside the number: "62,000/h in Max speed" states the
        // best case as a best case, instead of quietly promising it.
        val (mode, _) = fortressFarm.bestRateFor("minecraft:bone")!!

        assertEquals("Skeletons only, fast", mode.name)
    }

    @Test
    fun `an item no mode produces has no rate`() {
        assertNull(iceFarm.bestRateFor("minecraft:diamond"))
    }

    @Test
    fun `an idea with no modes has no rate`() {
        assertNull(emptyList<IdeaProductionMode>().bestRateFor("minecraft:ice"))
    }

    @Test
    fun `importing a single-mode farm takes that mode without asking`() {
        val single = listOf(mode("Default", 0, "minecraft:cobblestone" to 12_000))

        assertEquals(mapOf("minecraft:cobblestone" to 12_000), ratesForImport(single))
    }

    @Test
    fun `importing with an explicit mode takes exactly that mode`() {
        assertEquals(mapOf("minecraft:ice" to 18_000), ratesForImport(iceFarm, chosenModeName = "Slowed"))
    }

    @Test
    fun `an unknown mode name falls back rather than importing nothing`() {
        // A stale name from a form should not produce a farm that supplies nothing at all.
        assertEquals(mapOf("minecraft:ice" to 62_000), ratesForImport(iceFarm, chosenModeName = "Turbo"))
    }

    @Test
    fun `without a choice the most productive mode wins, across all its items`() {
        // "Everything, fast" totals 1,400/h against "Skeletons only, fast" at 940 — the comparison
        // is over the mode's whole output, not its best single item.
        assertEquals(
            mapOf("minecraft:bone" to 700, "minecraft:blaze_rod" to 500, "minecraft:nether_wart" to 200),
            ratesForImport(fortressFarm),
        )
    }

    @Test
    fun `importing an idea with no modes produces nothing`() {
        assertEquals(emptyMap(), ratesForImport(emptyList()))
    }

    @Test
    fun `a farm that produces something it never measured still produces it`() {
        // A small private bamboo farm: the author knows what it makes, not how fast. Ignoring it
        // because nobody timed it would hide the design for the wrong reason.
        val bambooFarm = listOf(mode("Default", 0, "minecraft:bamboo" to null))

        assertTrue(bambooFarm.produces("minecraft:bamboo"))
        assertNull(bambooFarm.bestRateFor("minecraft:bamboo"))
    }

    @Test
    fun `an unmeasured rate never wins the best-rate comparison`() {
        val mixed = listOf(
            mode("Measured", 0, "minecraft:ice" to 1_000),
            mode("Unmeasured", 1, "minecraft:ice" to null),
        )

        assertEquals("Measured", mixed.bestRateFor("minecraft:ice")!!.first.name)
    }

    @Test
    fun `an unmeasured rate imports as zero, which the project already reads as unknown`() {
        val bambooFarm = listOf(mode("Default", 0, "minecraft:bamboo" to null))

        assertEquals(mapOf("minecraft:bamboo" to 0), ratesForImport(bambooFarm))
    }

    @Test
    fun `an unmeasured mode does not beat a measured one when picking for import`() {
        val modes = listOf(
            mode("Unmeasured", 0, "minecraft:ice" to null, "minecraft:packed_ice" to null),
            mode("Measured", 1, "minecraft:ice" to 500),
        )

        assertEquals(mapOf("minecraft:ice" to 500), ratesForImport(modes))
    }

    @Test
    fun `the implicit mode is recognisable`() {
        assertEquals(true, mode(IdeaProductionMode.DEFAULT_MODE_NAME, 0, "minecraft:ice" to 1).isImplicit)
        assertEquals(false, mode("Max speed", 0, "minecraft:ice" to 1).isImplicit)
    }
}
