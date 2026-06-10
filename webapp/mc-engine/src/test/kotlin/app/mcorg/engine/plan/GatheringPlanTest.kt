package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.SourceNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GatheringPlanTest {

    private val craft = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "recipe.json")
    private val mine = SourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/oak_log.json")

    private fun item(name: String) = Item("minecraft:$name", name)

    /**
     * chest <- 8 planks (produces 1), stick <- 2 planks (produces 4),
     * planks <- 1 log (produces 4), log = RAW_GATHER.
     * Targets: 64 chests, 64 sticks. Quantities as quantify() would compute them.
     */
    private fun chestAndStickPlan(): GatheringPlan {
        val nodes = mapOf(
            "minecraft:chest" to PlanNode(
                item = item("chest"), quantity = 64, crafts = 64, leftover = 0,
                status = PlanNodeStatus.RESOLVED, source = craft, producedQuantity = 1,
                requires = listOf(PlanRequirement("minecraft:planks", 8))
            ),
            "minecraft:stick" to PlanNode(
                item = item("stick"), quantity = 64, crafts = 16, leftover = 0,
                status = PlanNodeStatus.RESOLVED, source = craft, producedQuantity = 4,
                requires = listOf(PlanRequirement("minecraft:planks", 2))
            ),
            "minecraft:planks" to PlanNode(
                item = item("planks"), quantity = 544, crafts = 136, leftover = 0,
                status = PlanNodeStatus.RESOLVED, source = craft, producedQuantity = 4,
                requires = listOf(PlanRequirement("minecraft:log", 1))
            ),
            "minecraft:log" to PlanNode(
                item = item("log"), quantity = 136, crafts = 136, leftover = 0,
                status = PlanNodeStatus.RAW_GATHER, source = mine
            )
        )
        return GatheringPlan(
            nodes = nodes,
            targets = listOf(PlanTarget(item("chest"), 64), PlanTarget(item("stick"), 64))
        )
    }

    // ── complete ────────────────────────────────────────────────────────────

    @Test
    fun `plan with only resolved, raw-gather and supplied nodes is complete`() {
        val nodes = mapOf(
            "minecraft:chest" to PlanNode(
                item = item("chest"), quantity = 1, crafts = 1, leftover = 0,
                status = PlanNodeStatus.RESOLVED, source = craft,
                requires = listOf(PlanRequirement("minecraft:planks", 8))
            ),
            "minecraft:planks" to PlanNode(
                item = item("planks"), quantity = 8, crafts = 8, leftover = 0,
                status = PlanNodeStatus.SUPPLIED, supply = SupplySource.Farm("Plank farm")
            )
        )
        val plan = GatheringPlan(nodes, listOf(PlanTarget(item("chest"), 1)))

        assertTrue(plan.complete)
    }

    @Test
    fun `an open tag makes the plan incomplete`() {
        val tag = MinecraftTag("minecraft:planks", "Planks", emptyList())
        val nodes = mapOf(
            "minecraft:planks" to PlanNode(
                item = tag, quantity = 12, crafts = 12, leftover = 0,
                status = PlanNodeStatus.OPEN_TAG
            )
        )
        val plan = GatheringPlan(nodes, listOf(PlanTarget(tag, 12)))

        assertFalse(plan.complete)
    }

    @Test
    fun `a blocked node makes the plan incomplete`() {
        val nodes = mapOf(
            "minecraft:dragon_egg" to PlanNode(
                item = item("dragon_egg"), quantity = 1, crafts = 1, leftover = 0,
                status = PlanNodeStatus.BLOCKED
            )
        )
        val plan = GatheringPlan(nodes, listOf(PlanTarget(item("dragon_egg"), 1)))

        assertFalse(plan.complete)
    }

    // ── activityList ────────────────────────────────────────────────────────

    @Test
    fun `activity list places every ingredient before its consumer`() {
        val activities = chestAndStickPlan().activityList
        val index = activities.withIndex().associate { (i, a) -> a.item.id to i }

        assertTrue(index.getValue("minecraft:log") < index.getValue("minecraft:planks"))
        assertTrue(index.getValue("minecraft:planks") < index.getValue("minecraft:chest"))
        assertTrue(index.getValue("minecraft:planks") < index.getValue("minecraft:stick"))
    }

    @Test
    fun `shared ingredient appears exactly once with the accumulated total`() {
        val activities = chestAndStickPlan().activityList

        val planks = activities.filter { it.item.id == "minecraft:planks" }
        assertEquals(1, planks.size)
        assertEquals(544, planks.single().quantity)
        assertEquals(4, activities.size)
    }

    @Test
    fun `activity list carries quantity, crafts and leftover for every node`() {
        val byId = chestAndStickPlan().activityList.associateBy { it.item.id }

        val stick = byId.getValue("minecraft:stick")
        assertEquals(64, stick.quantity)
        assertEquals(16, stick.crafts)
        assertEquals(0, stick.leftover)

        val log = byId.getValue("minecraft:log")
        assertEquals(136, log.quantity)
        assertEquals(PlanNodeStatus.RAW_GATHER, log.status)
    }

    @Test
    fun `open tag and blocked nodes appear in the activity list with quantities`() {
        val tag = MinecraftTag("minecraft:logs", "Logs", emptyList())
        val nodes = mapOf(
            "minecraft:chest" to PlanNode(
                item = item("chest"), quantity = 2, crafts = 2, leftover = 0,
                status = PlanNodeStatus.RESOLVED, source = craft,
                requires = listOf(
                    PlanRequirement("minecraft:logs", 2),
                    PlanRequirement("minecraft:netherite_hinge", 1)
                )
            ),
            "minecraft:logs" to PlanNode(
                item = tag, quantity = 4, crafts = 4, leftover = 0,
                status = PlanNodeStatus.OPEN_TAG
            ),
            "minecraft:netherite_hinge" to PlanNode(
                item = item("netherite_hinge"), quantity = 2, crafts = 2, leftover = 0,
                status = PlanNodeStatus.BLOCKED
            )
        )
        val plan = GatheringPlan(nodes, listOf(PlanTarget(item("chest"), 2)))

        val byId = plan.activityList.associateBy { it.item.id }
        assertEquals(4, byId.getValue("minecraft:logs").quantity)
        assertEquals(PlanNodeStatus.OPEN_TAG, byId.getValue("minecraft:logs").status)
        assertEquals(2, byId.getValue("minecraft:netherite_hinge").quantity)
        assertEquals(PlanNodeStatus.BLOCKED, byId.getValue("minecraft:netherite_hinge").status)
        assertFalse(plan.complete)
    }

    @Test
    fun `activity list ordering is deterministic`() {
        val first = chestAndStickPlan().activityList.map { it.item.id }
        repeat(5) {
            assertEquals(first, chestAndStickPlan().activityList.map { it.item.id })
        }
    }

    @Test
    fun `defensive - cyclic nodes are still all emitted`() {
        val nodes = mapOf(
            "minecraft:a" to PlanNode(
                item = item("a"), quantity = 1, crafts = 1, leftover = 0,
                status = PlanNodeStatus.RESOLVED, source = craft,
                requires = listOf(PlanRequirement("minecraft:b", 1))
            ),
            "minecraft:b" to PlanNode(
                item = item("b"), quantity = 1, crafts = 1, leftover = 0,
                status = PlanNodeStatus.RESOLVED, source = craft,
                requires = listOf(PlanRequirement("minecraft:a", 1))
            )
        )
        val plan = GatheringPlan(nodes, listOf(PlanTarget(item("a"), 1)))

        assertEquals(setOf("minecraft:a", "minecraft:b"), plan.activityList.map { it.item.id }.toSet())
    }

    // ── perTarget ───────────────────────────────────────────────────────────

    @Test
    fun `per-target tree expands the chain with stand-alone quantities`() {
        val tree = chestAndStickPlan().perTarget("minecraft:chest")

        assertNotNull(tree)
        assertEquals(64, tree.quantityIfAlone)
        assertEquals(64, tree.craftsIfAlone)

        val planks = tree.children.single()
        assertEquals("minecraft:planks", planks.item.id)
        assertEquals(512, planks.quantityIfAlone)
        assertEquals(128, planks.craftsIfAlone)

        val log = planks.children.single()
        assertEquals("minecraft:log", log.item.id)
        assertEquals(128, log.quantityIfAlone)
        assertEquals(PlanNodeStatus.RAW_GATHER, log.status)
    }

    @Test
    fun `per-target quantities ceil at each node`() {
        val tree = chestAndStickPlan().perTarget("minecraft:stick")

        assertNotNull(tree)
        // 64 sticks / 4 per craft = 16 crafts -> 32 planks -> 8 plank crafts -> 8 logs
        assertEquals(16, tree.craftsIfAlone)
        val planks = tree.children.single()
        assertEquals(32, planks.quantityIfAlone)
        assertEquals(8, planks.craftsIfAlone)
        assertEquals(8, planks.children.single().quantityIfAlone)
    }

    @Test
    fun `per-target returns null for an item that is not a target`() {
        assertNull(chestAndStickPlan().perTarget("minecraft:planks"))
    }

    @Test
    fun `per-target follows a tag-redirected root`() {
        val tag = MinecraftTag("minecraft:planks_tag", "Planks", emptyList())
        val nodes = mapOf(
            "minecraft:oak_planks" to PlanNode(
                item = item("oak_planks"), quantity = 12, crafts = 3, leftover = 0,
                status = PlanNodeStatus.RESOLVED, source = craft, producedQuantity = 4,
                requires = listOf(PlanRequirement("minecraft:log", 1))
            ),
            "minecraft:log" to PlanNode(
                item = item("log"), quantity = 3, crafts = 3, leftover = 0,
                status = PlanNodeStatus.RAW_GATHER, source = mine
            )
        )
        val plan = GatheringPlan(
            nodes = nodes,
            targets = listOf(PlanTarget(tag, 12)),
            roots = mapOf("minecraft:planks_tag" to "minecraft:oak_planks")
        )

        val tree = plan.perTarget("minecraft:planks_tag")
        assertNotNull(tree)
        assertEquals("minecraft:oak_planks", tree.item.id)
        assertEquals(12, tree.quantityIfAlone)
        assertEquals(3, tree.craftsIfAlone)
    }

    @Test
    fun `defensive - cyclic nodes do not hang per-target expansion`() {
        val nodes = mapOf(
            "minecraft:a" to PlanNode(
                item = item("a"), quantity = 1, crafts = 1, leftover = 0,
                status = PlanNodeStatus.RESOLVED, source = craft,
                requires = listOf(PlanRequirement("minecraft:b", 1))
            ),
            "minecraft:b" to PlanNode(
                item = item("b"), quantity = 1, crafts = 1, leftover = 0,
                status = PlanNodeStatus.RESOLVED, source = craft,
                requires = listOf(PlanRequirement("minecraft:a", 1))
            )
        )
        val plan = GatheringPlan(nodes, listOf(PlanTarget(item("a"), 1)))

        val tree = plan.perTarget("minecraft:a")
        assertNotNull(tree)
        val b = tree.children.single()
        assertEquals("minecraft:b", b.item.id)
        // the revisited node is cut to a leaf instead of recursing forever
        val aAgain = b.children.single()
        assertEquals("minecraft:a", aAgain.item.id)
        assertTrue(aAgain.children.isEmpty())
    }

    // ── ceilDiv ─────────────────────────────────────────────────────────────

    @Test
    fun `ceilDiv rounds up and reports exact division`() {
        assertEquals(3, ceilDiv(10, 4))
        assertEquals(2, ceilDiv(8, 4))
        assertEquals(1, ceilDiv(1, 64))
        assertEquals(0, ceilDiv(0, 4))
    }
}
