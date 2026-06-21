package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanRequirement
import app.mcorg.engine.plan.PlanTarget
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * [drillTreeFor] must resolve a drill subtree for ANY item in the plan — both the
 * project's defined targets and derived intermediates (which `perTarget` alone rejects).
 * Regression for "'#minecraft:planks' is not a target in this plan".
 */
class DrillTreeResolutionTest {

    // hopper (target) -> chest -> planks ; hopper -> iron_ingot
    private fun hopperPlan(): GatheringPlan {
        val nodes = mapOf(
            "minecraft:hopper" to PlanNode(
                item = Item("minecraft:hopper", "Hopper"),
                quantity = 1, crafts = 1, leftover = 0, status = PlanNodeStatus.RESOLVED,
                requires = listOf(
                    PlanRequirement("minecraft:chest", 1),
                    PlanRequirement("minecraft:iron_ingot", 5),
                ),
            ),
            "minecraft:chest" to PlanNode(
                item = Item("minecraft:chest", "Chest"),
                quantity = 1, crafts = 1, leftover = 0, status = PlanNodeStatus.RESOLVED,
                requires = listOf(PlanRequirement("minecraft:oak_planks", 8)),
            ),
            "minecraft:oak_planks" to PlanNode(
                item = Item("minecraft:oak_planks", "Oak Planks"),
                quantity = 8, crafts = 2, leftover = 0, status = PlanNodeStatus.RAW_GATHER,
            ),
            "minecraft:iron_ingot" to PlanNode(
                item = Item("minecraft:iron_ingot", "Iron Ingot"),
                quantity = 5, crafts = 5, leftover = 0, status = PlanNodeStatus.RAW_GATHER,
            ),
        )
        return GatheringPlan(nodes = nodes, targets = listOf(PlanTarget(Item("minecraft:hopper", "Hopper"), 1)))
    }

    @Test
    fun `resolves the defined target directly`() {
        val tree = hopperPlan().drillTreeFor("minecraft:hopper")
        assertEquals("minecraft:hopper", tree?.item?.id)
    }

    @Test
    fun `derived intermediate resolves to the full chain of its target, containing the item`() {
        val tree = hopperPlan().drillTreeFor("minecraft:oak_planks")
        // Full containing chain (rooted at the target), not the isolated planks subtree.
        assertEquals("minecraft:hopper", tree?.item?.id)
        assertNotNull(tree?.let { findNodeById(it, "minecraft:oak_planks") }, "planks present within the chain")
    }

    @Test
    fun `deeper derived node resolves to its target chain`() {
        val tree = hopperPlan().drillTreeFor("minecraft:iron_ingot")
        assertEquals("minecraft:hopper", tree?.item?.id)
        assertNotNull(tree?.let { findNodeById(it, "minecraft:iron_ingot") })
    }

    @Test
    fun `returns null for an item not in the plan at all`() {
        assertNull(hopperPlan().drillTreeFor("minecraft:diamond"))
    }

    // ── buildNodeIngredients ──────────────────────────────────────────────────

    private fun craftPlan(): GatheringPlan = GatheringPlan(
        nodes = mapOf(
            // produces 2 hoppers per craft, consuming 10 iron + 2 chest → 5 iron + 1 chest each
            "minecraft:hopper" to PlanNode(
                item = Item("minecraft:hopper", "Hopper"),
                quantity = 2, crafts = 1, leftover = 0, status = PlanNodeStatus.RESOLVED, producedQuantity = 2,
                requires = listOf(PlanRequirement("minecraft:iron_ingot", 10), PlanRequirement("minecraft:chest", 2)),
            ),
            // produces 8 planks per craft from 2 logs → 0.25 log each
            "minecraft:oak_planks" to PlanNode(
                item = Item("minecraft:oak_planks", "Oak Planks"),
                quantity = 8, crafts = 1, leftover = 0, status = PlanNodeStatus.RESOLVED, producedQuantity = 8,
                requires = listOf(PlanRequirement("minecraft:oak_log", 2)),
            ),
            "minecraft:iron_ingot" to PlanNode(Item("minecraft:iron_ingot", "Iron Ingot"), 10, 10, 0, PlanNodeStatus.RAW_GATHER),
            "minecraft:chest" to PlanNode(Item("minecraft:chest", "Chest"), 2, 2, 0, PlanNodeStatus.RAW_GATHER),
            "minecraft:oak_log" to PlanNode(Item("minecraft:oak_log", "Oak Log"), 2, 2, 0, PlanNodeStatus.RAW_GATHER),
        ),
        targets = listOf(PlanTarget(Item("minecraft:hopper", "Hopper"), 2)),
    )

    @Test
    fun `ingredients normalise per output and sort by name`() {
        assertEquals("1 Chest + 5 Iron Ingot", buildNodeIngredients(craftPlan())["minecraft:hopper"])
    }

    @Test
    fun `ingredients keep a clean decimal for sub-one ratios`() {
        assertEquals("0.25 Oak Log", buildNodeIngredients(craftPlan())["minecraft:oak_planks"])
    }

    @Test
    fun `terminals have no ingredient entry`() {
        assertNull(buildNodeIngredients(craftPlan())["minecraft:iron_ingot"])
    }
}
