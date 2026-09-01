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

    /** Smelt and Hunt are ~1,400px and already scannable; folding them is motion without benefit. */
    @Test
    fun `leaves a short section whole`() {
        val smeltShaped = listOf(activity("Smooth Stone", 23_479)) + (1..17).map { activity("Bit $it", 5) }

        val split = ActivitySectionLayout.of(smeltShaped)

        assertTrue(split.folded.isEmpty())
        assertEquals(18, split.lead.size)
    }

    /**
     * The honesty check. Hunt's real distribution is flat — 16 of its 17 rows are needed to
     * cover 90% — so a rule that folded it would be hiding work, not trivia. Scaled past the
     * length guard so it is the *distribution* being tested, not the row count.
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
