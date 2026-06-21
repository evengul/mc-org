package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanRequirement
import app.mcorg.engine.plan.PlanTarget
import kotlin.test.assertEquals
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
    fun `resolves a derived intermediate as a subtree of its target`() {
        val tree = hopperPlan().drillTreeFor("minecraft:oak_planks")
        assertEquals("minecraft:oak_planks", tree?.item?.id, "Derived planks should resolve via the hopper chain")
    }

    @Test
    fun `resolves a deeper derived node`() {
        val tree = hopperPlan().drillTreeFor("minecraft:iron_ingot")
        assertEquals("minecraft:iron_ingot", tree?.item?.id)
    }

    @Test
    fun `returns null for an item not in the plan at all`() {
        assertNull(hopperPlan().drillTreeFor("minecraft:diamond"))
    }
}
