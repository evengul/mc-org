package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.engine.plan.Activity
import app.mcorg.engine.plan.ActivityGroup
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNodeStatus
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What "next" means (MCO-481). */
class NextUpPickTest {

    private fun activity(
        name: String,
        quantity: Long,
        group: ActivityGroup = ActivityGroup.CRAFT,
        status: PlanNodeStatus = PlanNodeStatus.RESOLVED,
    ) = Activity(
        item = Item(id = "minecraft:${name.lowercase().replace(' ', '_')}", name = name),
        quantity = quantity,
        crafts = 1,
        leftover = 0,
        status = status,
        group = group,
    )

    private fun planOf(vararg activities: Activity): GatheringPlan {
        val plan = mockk<GatheringPlan>()
        every { plan.activityList } returns activities.toList()
        return plan
    }

    @Test
    fun `nothing to do when there is no plan`() {
        assertTrue(NextUpPick.of(null).isEmpty())
    }

    /**
     * A leaf — RAW_GATHER or SUPPLIED — has no unmet inputs, so it is work you can start now.
     * It wins over a bigger job that is waiting on something above it.
     */
    @Test
    fun `prefers work with nothing standing in front of it`() {
        val plan = planOf(
            activity("Oak Planks", 111_005),
            activity("Ice", 20_611, ActivityGroup.GATHER, PlanNodeStatus.RAW_GATHER),
        )

        assertEquals("Ice", NextUpPick.of(plan).first().item.name)
    }

    /**
     * The engine's list is topological with ties broken by name, so its head is whatever sorts
     * first alphabetically. Driving the widget against the real plan surfaced "1 Black
     * Terracotta" as the suggestion — actionable, and useless.
     */
    @Test
    fun `ranks work by size, not by position in the list`() {
        val plan = planOf(
            activity("Oak Planks", 111_005),
            activity("Chest", 10_890),
            activity("Hopper", 5_630),
        )

        assertEquals("Oak Planks", NextUpPick.of(plan).first().item.name)
    }

    /**
     * MCO-504 reversed this. The widget used to lead with unanswered variant questions, because
     * an unpicked variant makes everything below it provisional — right reasoning, wrong
     * conclusion: it asked the same question the "Needs attention" section was already asking,
     * in two places at once.
     *
     * A decision is not a move. It is not offered here at all, and while one is open the caller
     * suppresses the whole widget rather than letting it give provisional advice.
     */
    @Test
    fun `a decision is not offered as a move, however much it settles`() {
        val plan = planOf(
            activity("Oak Planks", 111_005),
            activity("Wooden Slabs", 3_540, ActivityGroup.NEEDS_ATTENTION, PlanNodeStatus.OPEN_TAG),
        )

        assertEquals(listOf("Oak Planks"), NextUpPick.of(plan).map { it.item.name })
    }

    /** A plan with nothing but questions has no move to offer, rather than offering a question. */
    @Test
    fun `a plan of only decisions offers nothing`() {
        val plan = planOf(
            activity("Stone Materials", 435, ActivityGroup.NEEDS_ATTENTION, PlanNodeStatus.OPEN_TAG),
            activity("Wooden Slabs", 3_540, ActivityGroup.NEEDS_ATTENTION, PlanNodeStatus.OPEN_TAG),
        )

        assertTrue(NextUpPick.of(plan).isEmpty(), "decisions are not moves")
    }

    @Test
    fun `skips what is already collected`() {
        val plan = planOf(activity("Oak Planks", 100), activity("Chest", 50))

        val next = NextUpPick.of(plan, progress = mapOf("minecraft:oak_planks" to 100))

        assertEquals("Chest", next.first().item.name)
    }

    @Test
    fun `partial progress still counts as outstanding`() {
        val plan = planOf(activity("Oak Planks", 100))

        val next = NextUpPick.of(plan, progress = mapOf("minecraft:oak_planks" to 99))

        assertEquals("Oak Planks", next.single().item.name)
    }

    /** A dead end is not a move — it is a thing to fix, and it has its own section. */
    @Test
    fun `does not offer blocked work as a move`() {
        val plan = planOf(
            activity("Impossible", 10, ActivityGroup.CRAFT, PlanNodeStatus.BLOCKED),
            activity("Chest", 50),
        )

        assertEquals(listOf("Chest"), NextUpPick.of(plan).map { it.item.name })
    }

    /**
     * This used to pin a cap of two decisions among the candidates, added because YAMS' 23 open
     * variant choices filled every slot and "Something else" only ever offered another question.
     * MCO-504 removed the cap by removing decisions, so the invariant it protected — every
     * candidate is something you can go and do — is now structural rather than budgeted.
     */
    @Test
    fun `every candidate is something you can go and do`() {
        val decisions = (1..10).map {
            activity("Choice $it", it.toLong(), ActivityGroup.NEEDS_ATTENTION, PlanNodeStatus.OPEN_TAG)
        }
        val work = (1..10).map { activity("Work $it", 100L) }
        val plan = planOf(*(decisions + work).toTypedArray())

        val next = NextUpPick.of(plan)

        assertTrue(next.isNotEmpty(), "always something to do")
        assertTrue(
            next.none { it.group == ActivityGroup.NEEDS_ATTENTION },
            "no candidate needs the user rather than the world",
        )
    }

    @Test
    fun `offers a bounded number of alternatives`() {
        val plan = planOf(*(1..40).map { activity("Thing $it", it.toLong()) }.toTypedArray())

        assertEquals(NextUpPick.CANDIDATES, NextUpPick.of(plan).size)
    }

    @Test
    fun `nothing outstanding means no widget`() {
        val plan = planOf(activity("Oak Planks", 100))

        assertTrue(NextUpPick.of(plan, progress = mapOf("minecraft:oak_planks" to 100)).isEmpty())
    }

    @Test
    fun `the reason says why this one, in the vocabulary of the pick`() {
        val farmed = activity("Cobblestone", 74_557, ActivityGroup.COLLECT_SUPPLIED, PlanNodeStatus.SUPPLIED)
        val raw = activity("Oak Log", 27_763, ActivityGroup.GATHER, PlanNodeStatus.RAW_GATHER)
        val craft = activity("Chest", 50)

        // No decision case: MCO-504 removed it. The "provisional" sentence it used to carry now
        // lives on the questions section's lead line, which is the only place asking.
        assertTrue(NextUpPick.reasonFor(farmed, isFirst = true).contains("farm already makes this"))
        assertTrue(NextUpPick.reasonFor(raw, isFirst = false).contains("Nothing has to happen first"))
        assertTrue(NextUpPick.reasonFor(craft, isFirst = true).contains("largest thing left"))
        assertTrue(NextUpPick.reasonFor(craft, isFirst = false).contains("ingredients are accounted for"))
    }
}
