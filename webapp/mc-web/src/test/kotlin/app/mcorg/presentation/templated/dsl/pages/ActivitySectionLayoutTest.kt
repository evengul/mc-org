package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.engine.plan.Activity
import app.mcorg.engine.plan.ActivityGroup
import app.mcorg.engine.plan.PlanNodeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The split behind MCO-480. The ordering guarantee is the one that would be silently wrong:
 * Craft and Smelt render topologically so an ingredient precedes what consumes it, and a fold
 * that re-sorted them would produce a work list you cannot follow.
 */
class ActivitySectionLayoutTest {

    private fun activity(name: String, quantity: Long) = Activity(
        item = Item(id = "minecraft:${name.lowercase().replace(' ', '_')}", name = name),
        quantity = quantity,
        crafts = 1,
        leftover = 0,
        status = PlanNodeStatus.RESOLVED,
        group = ActivityGroup.CRAFT,
    )

    /** A section shaped like the real Craft one: a few huge rows, a long tail of single items. */
    private fun craftShaped(): List<Activity> =
        listOf(activity("Glass", 111_005), activity("Stone", 20_166), activity("Planks", 13_357)) +
            (1..60).map { activity("Trinket $it", 1) }

    @Test
    fun `folds a long skewed section down to the rows that carry the work`() {
        val split = ActivitySectionLayout.of(craftShaped())

        assertEquals(5, split.lead.size, "coverage wants 3; the floor keeps 5 on screen")
        assertEquals(58, split.folded.size)
        assertTrue(split.leadShareOfItems >= 90)
    }

    /**
     * The guarantee that matters. Selection happens on a sorted copy; the rendered lists must
     * come back in the order they went in, or Craft stops reading as a sequence of steps.
     */
    @Test
    fun `preserves the incoming order in both halves`() {
        // Deliberately not sorted by quantity — this is what a topological Craft list looks like.
        val ordered = listOf(
            activity("Acacia Planks", 23),
            activity("Glass", 111_005),
            activity("Acacia Button", 1),
            activity("Stone", 20_166),
        ) + (1..30).map { activity("Filler $it", 1) }

        val split = ActivitySectionLayout.of(ordered)

        val leadNames = split.lead.map { it.item.name }
        assertEquals(leadNames.sortedBy { name -> ordered.indexOfFirst { it.item.name == name } }, leadNames)

        val foldedNames = split.folded.map { it.item.name }
        assertEquals(foldedNames.sortedBy { name -> ordered.indexOfFirst { it.item.name == name } }, foldedNames)
    }

    @Test
    fun `every row survives the split exactly once`() {
        val ordered = craftShaped()
        val split = ActivitySectionLayout.of(ordered)

        assertEquals(ordered.size, split.lead.size + split.folded.size)
        assertEquals(
            ordered.map { it.item.id }.toSet(),
            (split.lead + split.folded).map { it.item.id }.toSet(),
        )
    }

    /**
     * There used to be a 24-row floor below which nothing folded, and Smelt sat just under it —
     * 18 rows, ten of which need a single item, 1,386px of page. `MIN_LEAD` and `MIN_FOLDED`
     * are the only length guards now.
     */
    @Test
    fun `folds a section that is short but still mostly trivia`() {
        val smeltShaped = listOf(activity("Smooth Stone", 23_479)) + (1..17).map { activity("Bit $it", 5) }

        val split = ActivitySectionLayout.of(smeltShaped)

        assertEquals(ActivitySectionLayout.MIN_LEAD, split.lead.size)
        assertEquals(13, split.folded.size)
    }

    /** Below MIN_LEAD + MIN_FOLDED there is nothing a toggle could usefully hide. */
    @Test
    fun `leaves a genuinely short section whole`() {
        val split = ActivitySectionLayout.of((1..7).map { activity("Bit $it", it.toLong()) })

        assertTrue(split.folded.isEmpty())
        assertEquals(7, split.lead.size)
    }

    /**
     * Hunt: 16 wools needing 3, plus 9 and 5 — 62 items of the plan's 338,121, one shearing
     * trip, 1,478px of page. Coverage cannot fold it because the curve is flat; the question
     * coverage asks is which rows carry the material, not whether the section carries any.
     */
    @Test
    fun `folds away a section that is a rounding error in the plan`() {
        val hunt = listOf(activity("Copper Ingot", 9), activity("Honeycomb", 5)) +
            (1..16).map { activity("$it Wool", 3) }

        val split = ActivitySectionLayout.of(hunt, planTotal = 338_121)

        assertTrue(split.lead.isEmpty(), "nothing here is worth a row")
        assertEquals(18, split.folded.size)
        assertEquals(62L, split.foldedItems)
    }

    /** Small share, but one row is a real job — so the section keeps its rows. */
    @Test
    fun `a section carrying one substantial row is never all noise`() {
        val trade = listOf(activity("Emerald", 1_000)) + (1..16).map { activity("Trinket $it", 1) }

        val split = ActivitySectionLayout.of(trade, planTotal = 10_000_000)

        assertTrue(split.lead.isNotEmpty())
        assertEquals("Emerald", split.lead.first().item.name)
    }

    /** Every row tiny, but together they are real work — the share test refuses. */
    @Test
    fun `a thousand small rows are not noise just because each row is`() {
        val many = (1..1_000).map { activity("Bit $it", 10) }

        val split = ActivitySectionLayout.of(many, planTotal = 20_000)

        assertTrue(split.lead.isNotEmpty())
    }

    /** Without a plan total there is nothing to be a rounding error *of*. */
    @Test
    fun `no plan total means no whole-section fold`() {
        val hunt = (1..16).map { activity("$it Wool", 3) }

        assertTrue(ActivitySectionLayout.of(hunt).lead.isNotEmpty())
    }

    /**
     * The honesty check. Where a section's work really is spread evenly *and* substantial, the
     * coverage fold must not pretend otherwise — it caps out and admits it hides little. (Hunt
     * looks like this but is caught earlier, by the rounding-error rule; this is the case where
     * the flat rows matter.)
     */
    @Test
    fun `does not fold a section whose work is spread evenly`() {
        val flat = (1..40).map { activity("Mob $it", 10) }

        val split = ActivitySectionLayout.of(flat)

        assertEquals(25, split.lead.size, "capped at MAX_LEAD rather than expanding everything")
        assertEquals(15, split.folded.size)
        // The fold is honest about being a poor deal here: it hides little of the work.
        assertTrue(split.leadShareOfItems < 70)
    }

    @Test
    fun `a remainder too small to be worth a toggle stays open`() {
        val ordered = listOf(activity("Glass", 100_000)) + (1..25).map { activity("Bit $it", 1) }

        val split = ActivitySectionLayout.of(ordered)

        // 26 rows, lead floors at 5, so 21 fold — comfortably past MIN_FOLDED.
        assertEquals(21, split.folded.size)

        val barelyLonger = listOf(activity("Glass", 100_000)) + (1..25).map { activity("Bit $it", 4_000) }
        val flatSplit = ActivitySectionLayout.of(barelyLonger)
        assertTrue(flatSplit.folded.size >= ActivitySectionLayout.MIN_FOLDED || flatSplit.folded.isEmpty())
    }

    @Test
    fun `a section with no quantities at all still renders`() {
        val ordered = (1..30).map { activity("Unknown $it", 0) }

        val split = ActivitySectionLayout.of(ordered)

        assertEquals(30, split.lead.size + split.folded.size)
        assertEquals(100, split.leadShareOfItems)
    }
}
