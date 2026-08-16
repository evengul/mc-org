package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.world.World
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanTarget
import app.mcorg.engine.plan.SupplySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-401 — which raw demand is worth a farm.
 *
 * The rule is small, but every exclusion in it is load-bearing: the roll-up is meant to be a
 * list of candidate farm projects, and a single wrong entry ("build a gold farm" when one is
 * already running) makes the whole list untrustworthy.
 */
class FarmScaleDemandsTest {

    private val cobblestone = Item("minecraft:cobblestone", "Cobblestone")
    private val ironIngot = Item("minecraft:iron_ingot", "Iron Ingot")
    private val stick = Item("minecraft:stick", "Stick")
    private val ice = Item("minecraft:ice", "Ice")
    private val planks = MinecraftTag("#minecraft:planks", "Planks", emptyList())

    private val threshold = World.DEFAULT_FARM_SCALE_THRESHOLD

    private fun plan(vararg nodes: PlanNode) = GatheringPlan(
        nodes = nodes.associateBy { it.item.id },
        targets = nodes.map { PlanTarget(it.item, it.quantity) },
    )

    private fun node(
        item: app.mcorg.domain.model.minecraft.MinecraftId,
        quantity: Long,
        status: PlanNodeStatus = PlanNodeStatus.RAW_GATHER,
        supply: SupplySource? = null,
    ) = PlanNode(item = item, quantity = quantity, crafts = 0, leftover = 0, status = status, supply = supply)

    @Test
    fun `raw demand at or above the threshold is farm-scale`() {
        val result = FarmScaleDemands.of(plan(node(cobblestone, 74_557)), threshold)

        assertEquals(1, result.size)
        assertEquals(FarmScaleDemand("minecraft:cobblestone", "Cobblestone", 74_557), result.first())
    }

    @Test
    fun `exactly one shulker box qualifies`() {
        // The threshold is read as "a shulker box is enough to want a farm", so the boundary
        // itself is inside the set, not just past it.
        assertEquals(1, FarmScaleDemands.of(plan(node(ice, 1_728)), threshold).size)
        assertTrue(FarmScaleDemands.of(plan(node(ice, 1_727)), threshold).isEmpty())
    }

    @Test
    fun `an item an operational farm already supplies is not a suggestion`() {
        // The exclusion that matters most: it is already solved. A supplied item resolves to
        // SUPPLIED rather than RAW_GATHER, so it never reaches the threshold test — no special
        // case, and no "build a gold farm" next to the gold farm you already built.
        val result = FarmScaleDemands.of(
            plan(node(ironIngot, 32_967, PlanNodeStatus.SUPPLIED, SupplySource.Farm("Earlygame iron farm"))),
            threshold,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a crafted intermediate is never farm-scale however large`() {
        // 21,888 sticks is real demand, but you do not build a stick farm — you build a tree
        // farm, and the wood appears in the plan on its own.
        val result = FarmScaleDemands.of(plan(node(stick, 21_888, PlanNodeStatus.RESOLVED)), threshold)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `an unresolved tag is not classified`() {
        // #minecraft:planks is the single largest line on the YAMS import (121,774) and is
        // deliberately absent: OPEN_TAG is a question, not demand for a specific item. Picking
        // the variant turns it into raw demand this then sees.
        val result = FarmScaleDemands.of(plan(node(planks, 121_774, PlanNodeStatus.OPEN_TAG)), threshold)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a blocked item is not classified`() {
        // No feasible source — a farm is not the missing piece, a source is.
        val result = FarmScaleDemands.of(plan(node(ice, 20_611, PlanNodeStatus.BLOCKED)), threshold)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `the roll-up is ordered largest first`() {
        // Ordering is the feature: the top of this list is where the roadmap starts.
        val result = FarmScaleDemands.of(
            plan(node(ice, 20_611), node(cobblestone, 74_557), node(ironIngot, 32_967)),
            threshold,
        )

        assertEquals(listOf(74_557L, 32_967L, 20_611L), result.map { it.quantity })
    }

    @Test
    fun `a world can raise its own threshold`() {
        // A superflat testing world and a megabase do not want the same line.
        val plan = plan(node(ice, 20_611), node(cobblestone, 74_557))

        assertEquals(2, FarmScaleDemands.of(plan, threshold).size)
        assertEquals(listOf("minecraft:cobblestone"), FarmScaleDemands.of(plan, 50_000).map { it.itemId })
    }

    @Test
    fun `marked item ids match the roll-up`() {
        // The row marker and the roll-up must not be able to disagree — same rule, one place.
        val plan = plan(
            node(cobblestone, 74_557),
            node(stick, 21_888, PlanNodeStatus.RESOLVED),
            node(ice, 12),
        )

        assertEquals(
            FarmScaleDemands.of(plan, threshold).map { it.itemId }.toSet(),
            FarmScaleDemands.itemIdsIn(plan, threshold),
        )
    }

    @Test
    fun `an empty plan yields nothing`() {
        assertTrue(FarmScaleDemands.of(plan(), threshold).isEmpty())
    }
}
