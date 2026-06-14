package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.SourceNode
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanTarget
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [planProgressTotals].
 *
 * Verifies that collected values are sourced entirely from [progressMap], covering
 * both items that have a resource_gathering row and derived (engine-only) items that do not.
 */
class PlanProgressTotalsTest {

    private val mine = SourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/oak_log.json")
    private val craft = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "recipe.json")

    private fun item(name: String) = Item("minecraft:$name", name)

    /**
     * Simple plan: log (RAW_GATHER, 136 needed) + planks (RESOLVED, 544 needed).
     * Both are countable.
     */
    private fun simplePlan(): GatheringPlan {
        val nodes = mapOf(
            "minecraft:planks" to PlanNode(
                item = item("planks"), quantity = 544, crafts = 136, leftover = 0,
                status = PlanNodeStatus.RESOLVED, source = craft, producedQuantity = 4,
            ),
            "minecraft:log" to PlanNode(
                item = item("log"), quantity = 136, crafts = 136, leftover = 0,
                status = PlanNodeStatus.RAW_GATHER, source = mine
            )
        )
        return GatheringPlan(nodes = nodes, targets = listOf(PlanTarget(item("planks"), 544)))
    }

    // ── totalRequired ────────────────────────────────────────────────────────

    @Test
    fun `totalRequired sums quantities of all countable activities`() {
        val (required, _) = planProgressTotals(simplePlan(), emptyMap())
        // 544 planks + 136 logs = 680
        assertEquals(680L, required)
    }

    // ── totalCollected with progressMap ──────────────────────────────────────

    @Test
    fun `totalCollected is zero when progressMap is empty`() {
        val (_, collected) = planProgressTotals(simplePlan(), emptyMap())
        assertEquals(0L, collected)
    }

    @Test
    fun `totalCollected includes progress for a DEFINED item (in resource_gathering)`() {
        // log has 50 collected — a defined item
        val progressMap = mapOf("minecraft:log" to 50)
        val (_, collected) = planProgressTotals(simplePlan(), progressMap)
        assertEquals(50L, collected)
    }

    @Test
    fun `totalCollected includes progress for a DERIVED item (no resource_gathering row)`() {
        // planks is a derived intermediate — no resource_gathering row, but progress IS persisted
        val progressMap = mapOf("minecraft:planks" to 200)
        val (_, collected) = planProgressTotals(simplePlan(), progressMap)
        assertEquals(200L, collected)
    }

    @Test
    fun `totalCollected sums progress across multiple items including derived ones`() {
        // Both items have persisted progress
        val progressMap = mapOf(
            "minecraft:log" to 100,
            "minecraft:planks" to 300,
        )
        val (required, collected) = planProgressTotals(simplePlan(), progressMap)
        assertEquals(680L, required)
        assertEquals(400L, collected)
    }

    @Test
    fun `non-countable activities do not contribute to totals`() {
        val nodes = mapOf(
            "minecraft:log" to PlanNode(
                item = item("log"), quantity = 10, crafts = 10, leftover = 0,
                status = PlanNodeStatus.RAW_GATHER, source = mine
            ),
            "minecraft:planks" to PlanNode(
                item = item("planks"), quantity = 8, crafts = 8, leftover = 0,
                status = PlanNodeStatus.SUPPLIED
            ),
        )
        val plan = GatheringPlan(nodes = nodes, targets = listOf(PlanTarget(item("log"), 10)))
        val progressMap = mapOf(
            "minecraft:log" to 5,
            "minecraft:planks" to 3,
        )
        val (required, collected) = planProgressTotals(plan, progressMap)
        // SUPPLIED is not countable — only log (RAW_GATHER) counts
        assertEquals(10L, required)
        assertEquals(5L, collected)
    }

    // ── counterActivityRow renders persisted current ─────────────────────────

    @Test
    fun `counterActivityRow renders persisted current from progressMap`() {
        val activity = simplePlan().activityList.first { it.item.id == "minecraft:log" }
        val progressMap = mapOf("minecraft:log" to 42)

        val html = createHTML().div {
            counterActivityRow(worldId = 1, projectId = 2, activity = activity, progressMap = progressMap)
        }

        // Row must show "42 / 136" — not "0 / 136"
        assertTrue(html.contains("42 / 136"), "Expected '42 / 136' in rendered row but got: $html")
    }

    @Test
    fun `counterActivityRow renders 0 when item has no progress`() {
        val activity = simplePlan().activityList.first { it.item.id == "minecraft:log" }

        val html = createHTML().div {
            counterActivityRow(worldId = 1, projectId = 2, activity = activity, progressMap = emptyMap())
        }

        assertTrue(html.contains("0 / 136"), "Expected '0 / 136' in rendered row but got: $html")
    }
}
