package app.mcorg.pipeline.world.roadmap

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * MCO-572 — the panel split and a stopped farm's cost are differences between hand lists.
 *
 * The figures are frame 4A's, measured on the partially-built fixture: YAMS has 224,573 left by
 * hand now and 60,454 with the six farms being built finished.
 */
class HandListSplitTest {

    private val yams = 10
    private val library = 11
    private val witch = 20
    private val cobble = 21
    private val oldSandFarm = 30

    @Test
    fun `promised and yours either way add up to the hand list now`() {
        val split = HandListSplit.split(byHandNow = 224_573, asIfBuilt = 60_454)

        assertEquals(HandListSplit.Split(promised = 164_119, eitherWay = 60_454), split)
        assertEquals(224_573, split!!.promised + split.eitherWay)
    }

    @Test
    fun `an unknown as-if-built hand list is no split, not a zero`() {
        assertNull(HandListSplit.split(byHandNow = 224_573, asIfBuilt = null))
    }

    @Test
    fun `a hand list that grew is nothing promised, not a negative`() {
        // A supplied item can make a different recipe the cheapest, one wanting a raw material the
        // old one did not. Rare, but the two halves must still add up.
        assertEquals(HandListSplit.Split(promised = 0, eitherWay = 100), HandListSplit.split(100, 140))
    }

    @Test
    fun `each panel asks for the farms being built, and each stopped farm that touches its plan`() {
        val wanted = HandListSplit.wanted(
            drawn = listOf(yams, library),
            building = setOf(witch, cobble),
            stopped = listOf(oldSandFarm),
            productions = mapOf(oldSandFarm to setOf("minecraft:sand")),
            demandItems = mapOf(yams to setOf("minecraft:sand", "minecraft:glass"), library to setOf("minecraft:copper_block")),
        )

        assertEquals(listOf(setOf(witch, cobble), setOf(witch, cobble, oldSandFarm)), wanted[yams])
        assertEquals(listOf(setOf(witch, cobble)), wanted[library], "the Library uses no sand, so no derivation")
    }

    @Test
    fun `with nothing to build, the world as it is needs no derivation`() {
        val wanted = HandListSplit.wanted(listOf(yams), emptySet(), emptyList(), emptyMap(), emptyMap())

        assertEquals(emptyList(), wanted[yams])
    }

    @Test
    fun `a stopped farm costs what its running again would take off, over the panels it touches`() {
        val building = setOf(witch, cobble)
        val cost = HandListSplit.stoppedCost(
            farm = oldSandFarm,
            drawn = listOf(yams, library),
            building = building,
            base = mapOf(yams to 60_454L, library to 28_886L),
            totals = mapOf(yams to mapOf(building + oldSandFarm to 45_000L)),
            productions = mapOf(oldSandFarm to setOf("minecraft:sand")),
            demandItems = mapOf(yams to setOf("minecraft:sand")),
        )

        assertEquals(15_454, cost, "the Library is untouched, so only YAMS's difference counts")
    }

    @Test
    fun `a stopped farm touching no panel costs nothing, and an unmeasured one has no number`() {
        val untouched = HandListSplit.stoppedCost(
            oldSandFarm, listOf(yams), emptySet(), mapOf(yams to 100L), emptyMap(),
            mapOf(oldSandFarm to setOf("minecraft:sand")), mapOf(yams to setOf("minecraft:stone")),
        )
        val unmeasured = HandListSplit.stoppedCost(
            oldSandFarm, listOf(yams), emptySet(), mapOf(yams to 100L), emptyMap(),
            mapOf(oldSandFarm to setOf("minecraft:sand")), mapOf(yams to setOf("minecraft:sand")),
        )

        assertEquals(0, untouched)
        assertNull(unmeasured)
    }

    @Test
    fun `with no panel drawn there is nothing to measure against, so no number`() {
        assertNull(
            HandListSplit.stoppedCost(
                oldSandFarm, emptyList(), emptySet(), emptyMap(), emptyMap(),
                mapOf(oldSandFarm to setOf("minecraft:sand")), emptyMap(),
            )
        )
    }

    // ---- a total order's "hand list left after" (MCO-544, frame 5B) -----------------------------

    private val iron = 22
    private val chain = listOf(witch, cobble, iron)
    private val chainProductions = mapOf(
        witch to setOf("minecraft:redstone"),
        cobble to setOf("minecraft:cobblestone"),
        iron to setOf("minecraft:iron_ingot"),
    )
    private val chainDemand = mapOf(
        yams to setOf("minecraft:redstone", "minecraft:cobblestone", "minecraft:iron_ingot"),
        library to setOf("minecraft:cobblestone"),
    )

    @Test
    fun `a panel derives only the steps whose farm makes something in its plan`() {
        assertEquals(
            mapOf(
                yams to listOf(setOf(witch), setOf(witch, cobble), setOf(witch, cobble, iron)),
                library to listOf(setOf(witch, cobble)),
            ),
            HandListSplit.chainPrefixes(chain, listOf(yams, library), chainProductions, chainDemand),
        )
    }

    @Test
    fun `a step that touches a panel's plan not at all leaves its hand list as the step before did`() {
        val totals = mapOf(
            yams to mapOf(setOf(witch) to 800L, setOf(witch, cobble) to 500L, setOf(witch, cobble, iron) to 450L),
            library to mapOf(setOf(witch, cobble) to 100L),
        )

        assertEquals(
            listOf(800L + 300, 500L + 100, 450L + 100),
            HandListSplit.leftAfter(chain, mapOf(yams to 1_000L, library to 300L), totals, chainProductions, chainDemand),
        )
    }

    @Test
    fun `a step's hand list is clamped to the one now, and an unmeasured step has no number`() {
        val totals = mapOf(
            yams to mapOf(setOf(witch) to 1_200L, setOf(witch, cobble, iron) to 450L),
            library to mapOf(setOf(witch, cobble) to 100L),
        )

        assertEquals(
            listOf(1_000L + 300, null, 450L + 100),
            HandListSplit.leftAfter(chain, mapOf(yams to 1_000L, library to 300L), totals, chainProductions, chainDemand),
            "a grown hand list reads as nothing taken off; the unmeasured middle step reads as unknown",
        )
    }
}
