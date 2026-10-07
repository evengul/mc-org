package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.world.World
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanRequirement
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
        requires: List<String> = emptyList(),
        /** Executions of the node's source; a 1:1 recipe by default. */
        crafts: Long = quantity,
        perCraft: Int = 1,
    ) = PlanNode(
        item = item, quantity = quantity, crafts = crafts, leftover = 0, status = status, supply = supply,
        requires = requires.map { PlanRequirement(it, perCraft) },
    )

    /** The rule before MCO-565: every material counts. Its own tests are at the bottom. */
    private val everything: (String) -> Boolean = { true }

    @Test
    fun `raw demand at or above the threshold is farm-scale`() {
        val result = FarmScaleDemands.of(plan(node(cobblestone, 74_557)), threshold, isRenewable = everything)

        assertEquals(1, result.size)
        assertEquals(FarmScaleDemand("minecraft:cobblestone", "Cobblestone", 74_557), result.first())
    }

    @Test
    fun `exactly one shulker box qualifies`() {
        // The threshold is read as "a shulker box is enough to want a farm", so the boundary
        // itself is inside the set, not just past it.
        assertEquals(1, FarmScaleDemands.of(plan(node(ice, 1_728)), threshold, isRenewable = everything).size)
        assertTrue(FarmScaleDemands.of(plan(node(ice, 1_727)), threshold, isRenewable = everything).isEmpty())
    }

    @Test
    fun `an item an operational farm already supplies is not a suggestion`() {
        // The exclusion that matters most: it is already solved. A supplied item resolves to
        // SUPPLIED rather than RAW_GATHER, so it never reaches the threshold test — no special
        // case, and no "build a gold farm" next to the gold farm you already built.
        val result = FarmScaleDemands.of(
            plan(node(ironIngot, 32_967, PlanNodeStatus.SUPPLIED, SupplySource.Farm("Earlygame iron farm"))),
            threshold,
            isRenewable = everything,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a crafted intermediate is never farm-scale however large`() {
        // 21,888 sticks is real demand, but you do not build a stick farm — you build a tree
        // farm, and the wood appears in the plan on its own.
        val result = FarmScaleDemands.of(plan(node(stick, 21_888, PlanNodeStatus.RESOLVED)), threshold, isRenewable = everything)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `an unresolved tag is not classified`() {
        // #minecraft:planks is the single largest line on the YAMS import (121,774) and is
        // deliberately absent: OPEN_TAG is a question, not demand for a specific item. Picking
        // the variant turns it into raw demand this then sees.
        val result = FarmScaleDemands.of(plan(node(planks, 121_774, PlanNodeStatus.OPEN_TAG)), threshold, isRenewable = everything)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a blocked item is not classified`() {
        // No feasible source — a farm is not the missing piece, a source is.
        val result = FarmScaleDemands.of(plan(node(ice, 20_611, PlanNodeStatus.BLOCKED)), threshold, isRenewable = everything)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `the roll-up is ordered largest first`() {
        // Ordering is the feature: the top of this list is where the roadmap starts.
        val result = FarmScaleDemands.of(
            plan(node(ice, 20_611), node(cobblestone, 74_557), node(ironIngot, 32_967)),
            threshold,
            isRenewable = everything,
        )

        assertEquals(listOf(74_557L, 32_967L, 20_611L), result.map { it.quantity })
    }

    @Test
    fun `a world can raise its own threshold`() {
        // A superflat testing world and a megabase do not want the same line.
        val plan = plan(node(ice, 20_611), node(cobblestone, 74_557))

        assertEquals(2, FarmScaleDemands.of(plan, threshold, isRenewable = everything).size)
        assertEquals(listOf("minecraft:cobblestone"), FarmScaleDemands.of(plan, 50_000, isRenewable = everything).map { it.itemId })
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
            FarmScaleDemands.of(plan, threshold, isRenewable = everything).map { it.itemId }.toSet(),
            FarmScaleDemands.itemIdsIn(plan, threshold, isRenewable = everything),
        )
    }

    @Test
    fun `an empty plan yields nothing`() {
        assertTrue(FarmScaleDemands.of(plan(), threshold, isRenewable = everything).isEmpty())
    }

    // ---- what the world has decided against (MCO-407) --------------------------------

    @Test
    fun `a dismissed item leaves the roll-up`() {
        val plan = plan(node(cobblestone, 74_557), node(ice, 20_611))

        val result = FarmScaleDemands.of(plan, threshold, dismissed = setOf("minecraft:ice"), isRenewable = everything)

        assertEquals(listOf("minecraft:cobblestone"), result.map { it.itemId })
    }

    @Test
    fun `a dismissed item loses its row badge too`() {
        // The badge and the roll-up are one rule in one place, and a dismissal that cleared the
        // list but left "Farm-scale" on the row would be the contradiction MCO-407 is removing.
        val plan = plan(node(cobblestone, 74_557), node(ice, 20_611))

        assertEquals(
            setOf("minecraft:cobblestone"),
            FarmScaleDemands.itemIdsIn(plan, threshold, dismissed = setOf("minecraft:ice"), isRenewable = everything),
        )
    }

    @Test
    fun `a dismissal survives the threshold moving`() {
        // The whole point: raising the threshold is the blunt instrument dismissal replaces, so
        // a dismissal that a threshold change undid would be a slower way of raising it.
        val plan = plan(node(cobblestone, 74_557), node(ice, 20_611))
        val dismissed = setOf("minecraft:ice")

        assertTrue(FarmScaleDemands.of(plan, 10_000, dismissed, isRenewable = everything).none { it.itemId == "minecraft:ice" })
        assertTrue(FarmScaleDemands.of(plan, 100, dismissed, isRenewable = everything).none { it.itemId == "minecraft:ice" })
        assertTrue(FarmScaleDemands.of(plan, 20_611, dismissed, isRenewable = everything).none { it.itemId == "minecraft:ice" })
    }

    @Test
    fun `dismissing something that was never farm-scale changes nothing`() {
        val plan = plan(node(cobblestone, 74_557), node(ice, 12))

        assertEquals(
            FarmScaleDemands.of(plan, threshold, isRenewable = everything).map { it.itemId },
            FarmScaleDemands.of(plan, threshold, dismissed = setOf("minecraft:ice"), isRenewable = everything).map { it.itemId },
        )
        assertTrue(
            FarmScaleDemands.dismissedIn(plan, threshold, setOf("minecraft:ice"), isRenewable = everything).isEmpty(),
            "12 ice was never a line, so nothing is being suppressed and the fold says nothing",
        )
    }

    @Test
    fun `what a dismissal is suppressing is still readable`() {
        // The undo list prints today's demand beside the demand it was dismissed at, which is
        // the reason a dismissal can be permanent without becoming a trap.
        val plan = plan(node(cobblestone, 74_557), node(ice, 20_611))

        val suppressed = FarmScaleDemands.dismissedIn(plan, threshold, setOf("minecraft:ice"), isRenewable = everything)

        assertEquals(listOf(FarmScaleDemand("minecraft:ice", "Ice", 20_611)), suppressed)
    }

    // ---- only what a farm can make (MCO-565) -------------------------------------------

    private val tuff = Item("minecraft:tuff", "Tuff")
    private val tuffBricks = Item("minecraft:tuff_bricks", "Tuff Bricks")
    private val rawIron = Item("minecraft:raw_iron", "Raw Iron")
    private val netherPortal = Item("minecraft:nether_portal", "Nether Portal")
    private val renewable = setOf("minecraft:cobblestone", "minecraft:iron_ingot", "minecraft:ice", "minecraft:nether_portal")
    private val isRenewable: (String) -> Boolean = renewable::contains

    @Test
    fun `a material no farm can make is never worth a farm, however much of it is wanted`() {
        // The report: 13,000 tuff under "Worth a farm". The advice cannot be taken.
        val result = FarmScaleDemands.of(plan(node(tuff, 13_000), node(cobblestone, 74_557)), threshold, isRenewable = isRenewable)

        assertEquals(listOf("minecraft:cobblestone"), result.map { it.itemId })
    }

    @Test
    fun `raw iron stays, because the ingot it is smelted into is farmed`() {
        // Raw iron comes off an ore and is not renewable itself. An iron farm makes the ingot,
        // and the raw iron line is the demand that farm answers.
        val plan = plan(
            node(rawIron, 32_967),
            node(ironIngot, 32_967, PlanNodeStatus.RESOLVED, requires = listOf("minecraft:raw_iron")),
        )

        assertEquals(listOf("minecraft:raw_iron"), FarmScaleDemands.of(plan, threshold, isRenewable = isRenewable).map { it.itemId })
    }

    @Test
    fun `tuff into tuff bricks stays out, because nothing makes the bricks either`() {
        val plan = plan(
            node(tuff, 13_000),
            node(tuffBricks, 13_000, PlanNodeStatus.RESOLVED, requires = listOf("minecraft:tuff")),
        )

        assertTrue(FarmScaleDemands.of(plan, threshold, isRenewable = isRenewable).isEmpty())
    }

    private val sand = Item("minecraft:sand", "Sand")
    private val glass = Item("minecraft:glass", "Glass")
    private val concretePowder = Item("minecraft:white_concrete_powder", "White Concrete Powder")
    private val diamond = Item("minecraft:diamond", "Diamond")
    private val diamondBlock = Item("minecraft:diamond_block", "Diamond Block")
    private val diamondPickaxe = Item("minecraft:diamond_pickaxe", "Diamond Pickaxe")
    private val withGlassAndPickaxe: (String) -> Boolean =
        (renewable + setOf("minecraft:glass", "minecraft:diamond_pickaxe"))::contains

    @Test
    fun `a few panes of glass do not make all the sand in a concrete build worth a farm`() {
        // Glass is renewable (a trading hall) and sand is not. Only the sand the glass takes could
        // be replaced by a farm, and 100 is nowhere near the threshold.
        val plan = plan(
            node(sand, 10_100),
            node(glass, 100, PlanNodeStatus.RESOLVED, requires = listOf("minecraft:sand")),
            node(concretePowder, 10_000, PlanNodeStatus.RESOLVED, requires = listOf("minecraft:sand")),
        )

        assertTrue(FarmScaleDemands.of(plan, threshold, isRenewable = withGlassAndPickaxe).isEmpty())
    }

    @Test
    fun `a large enough share qualifies, and the line shows the plan's quantity`() {
        // 5,000 of the sand becomes glass, which is over the threshold. The line prints 15,000
        // like the row it badges, and like the quantity a dismissal of it records.
        val plan = plan(
            node(sand, 15_000),
            node(glass, 5_000, PlanNodeStatus.RESOLVED, requires = listOf("minecraft:sand")),
            node(concretePowder, 10_000, PlanNodeStatus.RESOLVED, requires = listOf("minecraft:sand")),
        )

        assertEquals(
            listOf(FarmScaleDemand("minecraft:sand", "Sand", 15_000)),
            FarmScaleDemands.of(plan, threshold, isRenewable = withGlassAndPickaxe),
        )
        assertEquals(
            listOf(FarmScaleDemand("minecraft:sand", "Sand", 15_000)),
            FarmScaleDemands.dismissedIn(plan, threshold, setOf("minecraft:sand"), isRenewable = withGlassAndPickaxe),
        )
    }

    @Test
    fun `the share is what a consumer's executions take, not its output`() {
        // 100 executions of a recipe taking 6 sand and making 16: 600 sand, under the threshold,
        // though the 1,600 items it makes would read as 9,600 sand if output were counted.
        val plan = plan(
            node(sand, 10_600),
            node(glass, 1_600, PlanNodeStatus.RESOLVED, requires = listOf("minecraft:sand"), crafts = 100, perCraft = 6),
            node(concretePowder, 10_000, PlanNodeStatus.RESOLVED, requires = listOf("minecraft:sand")),
        )

        assertTrue(FarmScaleDemands.of(plan, threshold, isRenewable = withGlassAndPickaxe).isEmpty())
    }

    @Test
    fun `one diamond pickaxe does not make a build's diamonds worth a farm`() {
        // Per execution: a block takes 9 diamonds, a pickaxe 3.
        val plan = plan(
            node(diamond, 18_003),
            node(diamondBlock, 2_000, PlanNodeStatus.RESOLVED, requires = listOf("minecraft:diamond"), perCraft = 9),
            node(diamondPickaxe, 1, PlanNodeStatus.RESOLVED, requires = listOf("minecraft:diamond"), perCraft = 3),
        )

        assertTrue(FarmScaleDemands.of(plan, threshold, isRenewable = withGlassAndPickaxe).isEmpty())
    }

    @Test
    fun `the nether portal is lit, not farmed`() {
        // Its synthetic source makes it read as renewable; it is still not a material.
        assertTrue(FarmScaleDemands.of(plan(node(netherPortal, 5_000)), threshold, isRenewable = isRenewable).isEmpty())
    }

    @Test
    fun `the badge and the undo list follow the same rule`() {
        val plan = plan(node(tuff, 13_000), node(ice, 20_611))

        assertEquals(setOf("minecraft:ice"), FarmScaleDemands.itemIdsIn(plan, threshold, isRenewable = isRenewable))
        assertTrue(
            FarmScaleDemands.dismissedIn(plan, threshold, setOf("minecraft:tuff"), isRenewable = isRenewable).isEmpty(),
            "a dismissed tuff line is not one the roll-up would show, so it is not being suppressed",
        )
    }
}
