package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.SourceNode
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanRequirement
import app.mcorg.engine.plan.PlanTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildFeedsLabelsTest {

    private val craft = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "recipe.json")
    private val mine = SourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/log.json")

    private fun item(id: String, name: String) = Item("minecraft:$id", name)

    private fun resolved(id: String, name: String, vararg requires: Pair<String, Int>) = PlanNode(
        item = item(id, name), quantity = 1, crafts = 1, leftover = 0,
        status = PlanNodeStatus.RESOLVED, source = craft,
        requires = requires.map { PlanRequirement("minecraft:${it.first}", it.second) }
    )

    private fun rawGather(id: String, name: String) = PlanNode(
        item = item(id, name), quantity = 1, crafts = 1, leftover = 0,
        status = PlanNodeStatus.RAW_GATHER, source = mine
    )

    /**
     * Four end-item targets (stick 64, chest 40, door 24, boat 10) all consume planks,
     * which consumes a log. So both planks and the log feed all four targets.
     */
    private fun fourTargetPlan(): GatheringPlan {
        val nodes = mapOf(
            "minecraft:stick" to resolved("stick", "Stick", "planks" to 2),
            "minecraft:chest" to resolved("chest", "Chest", "planks" to 8),
            "minecraft:door" to resolved("door", "Birch Door", "planks" to 6),
            "minecraft:boat" to resolved("boat", "Boat", "planks" to 5),
            "minecraft:planks" to resolved("planks", "Birch Planks", "log" to 1),
            "minecraft:log" to rawGather("log", "Birch Log"),
        )
        return GatheringPlan(
            nodes = nodes,
            targets = listOf(
                PlanTarget(item("stick", "Stick"), 64),
                PlanTarget(item("chest", "Chest"), 40),
                PlanTarget(item("door", "Birch Door"), 24),
                PlanTarget(item("boat", "Boat"), 10),
            )
        )
    }

    @Test
    fun `label lists feeding targets by amount descending, capped with a plus-N-more tail`() {
        val labels = buildFeedsLabels(fourTargetPlan())

        // 64 Stick, 40 Chest, 24 Birch Door shown; 10 Boat folded into "+1 more".
        assertEquals("Feeds 64 Stick · 40 Chest · 24 Birch Door · +1 more", labels["minecraft:log"]?.text)
    }

    @Test
    fun `a truncated label carries the full uncapped list as a tooltip title`() {
        val label = buildFeedsLabels(fourTargetPlan())["minecraft:log"]!!
        // The hidden "10 Boat" is recoverable from the title even though it's not in the text.
        assertEquals("Feeds 64 Stick · 40 Chest · 24 Birch Door · 10 Boat", label.title)
    }

    @Test
    fun `a shared intermediate carries the same feeds label as the leaf below it`() {
        val labels = buildFeedsLabels(fourTargetPlan())
        assertEquals(labels["minecraft:log"], labels["minecraft:planks"])
    }

    @Test
    fun `targets that feed nothing are absent from the map`() {
        val labels = buildFeedsLabels(fourTargetPlan())
        listOf("minecraft:stick", "minecraft:chest", "minecraft:door", "minecraft:boat").forEach {
            assertNull(labels[it], "$it is a pure target and should have no feeds label")
        }
    }

    @Test
    fun `no plus-N-more tail or tooltip when the feeder count is within the cap`() {
        val nodes = mapOf(
            "minecraft:chest" to resolved("chest", "Chest", "planks" to 8),
            "minecraft:planks" to resolved("planks", "Birch Planks", "log" to 1),
            "minecraft:log" to rawGather("log", "Birch Log"),
        )
        val plan = GatheringPlan(nodes, listOf(PlanTarget(item("chest", "Chest"), 40)))

        val label = buildFeedsLabels(plan)["minecraft:log"]!!
        assertEquals("Feeds 40 Chest", label.text)
        assertFalse(label.text.contains("more"))
        assertNull(label.title, "an untruncated list needs no tooltip")
    }

    @Test
    fun `the cap is configurable`() {
        val labels = buildFeedsLabels(fourTargetPlan(), cap = 2)
        assertEquals("Feeds 64 Stick · 40 Chest · +2 more", labels["minecraft:log"]?.text)
    }

    @Test
    fun `an empty plan yields no labels`() {
        val plan = GatheringPlan(emptyMap(), emptyList())
        assertTrue(buildFeedsLabels(plan).isEmpty())
    }
}
