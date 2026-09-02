package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.engine.plan.Activity
import app.mcorg.engine.plan.ActivityGroup
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNode
import app.mcorg.engine.plan.PlanNodeStatus
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The arithmetic behind MCO-478. These matter more than the template: the whole change is a
 * decision about which of 555 rows a reader sees, and getting the guards wrong either leaves
 * the 25,000px page alone or hides work somebody is in the middle of.
 */
class ResourceListLayoutTest {

    // ---- fixtures ----------------------------------------------------------------------

    private var nextId = 1

    private fun row(
        name: String,
        required: Int,
        collected: Int = 0,
        itemId: String = "minecraft:${name.lowercase().replace(' ', '_')}",
        ignored: Boolean = false,
    ) = ResourceGatheringItem(
        id = nextId++,
        projectId = 1,
        itemId = itemId,
        name = name,
        required = required,
        collected = collected,
        ignored = ignored,
    )

    /** A plan is expensive to build for real; only the item -> group mapping is under test. */
    private fun planOf(vararg groups: Pair<String, ActivityGroup>): GatheringPlan {
        val plan = mockk<GatheringPlan>()
        every { plan.activityList } returns groups.map { (itemId, group) ->
            Activity(
                item = Item(id = itemId, name = itemId.substringAfter(':')),
                quantity = 1,
                crafts = 1,
                leftover = 0,
                status = PlanNodeStatus.RESOLVED,
                group = group,
            )
        }
        return plan
    }

    private fun trivia(count: Int) = (1..count).map { row("Trinket $it", required = 1) }

    // ---- folding -----------------------------------------------------------------------

    @Test
    fun `folds the single-item tail once there is enough of it to be worth hiding`() {
        val resources = listOf(row("Smooth Stone", 4_608)) + trivia(20)

        val layout = ResourceListLayout.of(resources, plan = null)

        assertEquals(20, layout.folded.size)
        assertEquals(1, layout.visibleCount)
        assertEquals(20L, layout.foldedItems)
        assertEquals(4_628L, layout.totalItems)
    }

    /**
     * The guard that stops this being tuned to one project. A build with a handful of
     * single-item rows has no tail worth hiding, and a disclosure over four rows is just a
     * thing to click.
     */
    @Test
    fun `leaves a short tail alone`() {
        val resources = listOf(row("Smooth Stone", 4_608)) + trivia(4)

        val layout = ResourceListLayout.of(resources, plan = null)

        assertTrue(layout.folded.isEmpty())
        assertEquals(5, layout.visibleCount)
    }

    /** Folding everything would leave an empty table, which is worse than a long one. */
    @Test
    fun `never folds the list out of existence`() {
        val layout = ResourceListLayout.of(trivia(40), plan = null)

        assertTrue(layout.folded.isEmpty())
        assertEquals(40, layout.visibleCount)
    }

    /** Progress means work in flight; burying it would lose the only sign of that. */
    @Test
    fun `keeps a single-item row that has progress against it`() {
        val started = row("Beacon", required = 1, collected = 1)
        val layout = ResourceListLayout.of(trivia(20) + started, plan = null)

        assertEquals(20, layout.folded.size)
        assertTrue(layout.groups.single().rows.contains(started))
    }

    /** The one kind of row that exists to be answered must never be hidden behind a click. */
    @Test
    fun `keeps a single-item row that is awaiting a decision`() {
        val open = row("Any Planks", required = 1, itemId = "minecraft:planks")
        val plan = planOf("minecraft:planks" to ActivityGroup.NEEDS_ATTENTION)

        val layout = ResourceListLayout.of(trivia(20) + open, plan)

        assertEquals(20, layout.folded.size)
        assertTrue(layout.groups.any { it.rows.contains(open) })
    }

    @Test
    fun `ignored and zero-quantity rows are not part of the list at all`() {
        val resources = listOf(
            row("Smooth Stone", 4_608),
            row("Dropped", required = 12, ignored = true),
            row("Zeroed", required = 0),
        )

        val layout = ResourceListLayout.of(resources, plan = null)

        assertEquals(1, layout.visibleCount)
        assertEquals(4_608L, layout.totalItems)
    }

    // ---- grouping ----------------------------------------------------------------------

    @Test
    fun `groups by how you get the item, in session order`() {
        val resources = listOf(
            row("Oak Planks", 640, itemId = "minecraft:oak_planks"),
            row("Iron Ingot", 320, itemId = "minecraft:iron_ingot"),
            row("Rotten Flesh", 64, itemId = "minecraft:rotten_flesh"),
        )
        val plan = planOf(
            "minecraft:oak_planks" to ActivityGroup.CRAFT,
            "minecraft:iron_ingot" to ActivityGroup.COLLECT_SUPPLIED,
            "minecraft:rotten_flesh" to ActivityGroup.HUNT,
        )

        val layout = ResourceListLayout.of(resources, plan)

        // ActivityGroup's declaration order is the preferred session order: collect, hunt, craft.
        assertEquals(
            listOf(ActivityGroup.COLLECT_SUPPLIED, ActivityGroup.HUNT, ActivityGroup.CRAFT),
            layout.groups.map { it.group },
        )
        assertTrue(layout.isGrouped)
    }

    @Test
    fun `rows the plan says nothing about get their own trailing group`() {
        val resources = listOf(
            row("Iron Ingot", 320, itemId = "minecraft:iron_ingot"),
            row("Mystery Block", 64, itemId = "minecraft:mystery"),
        )
        val plan = planOf("minecraft:iron_ingot" to ActivityGroup.COLLECT_SUPPLIED)

        val layout = ResourceListLayout.of(resources, plan)

        assertNull(layout.groups.last().group)
        assertEquals(2, layout.visibleCount)
    }

    /** No plan means nothing to group *by*; one heading over the whole list is a label, not a grouping. */
    @Test
    fun `reports itself ungrouped when the plan is missing`() {
        val layout = ResourceListLayout.of(listOf(row("Smooth Stone", 4_608)), plan = null)

        assertFalse(layout.isGrouped)
        assertNull(layout.groups.single().group)
    }

    /** Grouping re-buckets rows; it must never re-rank them. */
    @Test
    fun `preserves the incoming order inside a group`() {
        val resources = listOf(
            row("Heaviest", 4_608, itemId = "minecraft:a"),
            row("Middle", 640, itemId = "minecraft:b"),
            row("Lightest", 64, itemId = "minecraft:c"),
        )
        val plan = planOf(
            "minecraft:a" to ActivityGroup.CRAFT,
            "minecraft:b" to ActivityGroup.CRAFT,
            "minecraft:c" to ActivityGroup.CRAFT,
        )

        val layout = ResourceListLayout.of(resources, plan)

        assertEquals(
            listOf("Heaviest", "Middle", "Lightest"),
            layout.groups.single().rows.map { it.name },
        )
    }

    @Test
    fun `group carries its own item total`() {
        val plan = planOf(
            "minecraft:a" to ActivityGroup.GATHER,
            "minecraft:b" to ActivityGroup.GATHER,
        )
        val resources = listOf(
            row("Cobblestone", 4_608, itemId = "minecraft:a"),
            row("Sand", 1_728, itemId = "minecraft:b"),
        )

        val layout = ResourceListLayout.of(resources, plan)

        assertEquals(6_336L, layout.groups.single().items)
    }

    /** The shape that motivated the issue: the tail dominates the page, not the work. */
    @Test
    fun `the YAMS shape leaves the heavy rows visible and hides the trivia`() {
        val heavy = (1..24).map { row("Heavy $it", required = 4_000, itemId = "minecraft:heavy$it") }
        val layout = ResourceListLayout.of(heavy + trivia(464), plan = null)

        assertEquals(464, layout.folded.size)
        assertEquals(24, layout.visibleCount)
        // The folded 464 are a rounding error against the build.
        assertTrue(layout.foldedItems * 100 < layout.totalItems)
    }
}
