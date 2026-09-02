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

    /**
     * Emptying a farm is work with a quantity, so SUPPLIED counts toward the header.
     *
     * It did not until the work-row redesign, on MCO-403's reasoning that a supplied item
     * terminates its chain. True, and not a reason to leave it out: hauling 50,000 gunpowder out
     * of a farm is a dozen trips, and a header reading 0% throughout is simply wrong.
     */
    @Test
    fun `supplied activities count toward the totals`() {
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
        assertEquals(18L, required, "10 mined + 8 hauled from the farm")
        assertEquals(8L, collected, "5 mined + 3 hauled")
    }

    /** A question's quantity is provisional, so it still stays out of the totals. */
    @Test
    fun `open and blocked activities do not contribute to totals`() {
        val nodes = mapOf(
            "minecraft:log" to PlanNode(
                item = item("log"), quantity = 10, crafts = 10, leftover = 0,
                status = PlanNodeStatus.RAW_GATHER, source = mine
            ),
            "#minecraft:planks" to PlanNode(
                item = item("planks"), quantity = 8, crafts = 8, leftover = 0,
                status = PlanNodeStatus.OPEN_TAG
            ),
            "minecraft:impossible" to PlanNode(
                item = item("impossible"), quantity = 4, crafts = 4, leftover = 0,
                status = PlanNodeStatus.BLOCKED
            ),
        )
        val plan = GatheringPlan(nodes = nodes, targets = listOf(PlanTarget(item("log"), 10)))
        val (required, collected) = planProgressTotals(plan, mapOf("minecraft:log" to 5))
        assertEquals(10L, required)
        assertEquals(5L, collected)
    }

    // ── the collapsed line reflects persisted progress ───────────────────────
    //
    // The row leads with what is *left* rather than what is collected — that is the number
    // answering "am I done yet" — so these read the remainder.

    @Test
    fun `a line shows the remainder after persisted progress`() {
        val activity = simplePlan().activityList.first { it.item.id == "minecraft:log" }

        val html = createHTML().div {
            workRowCollapsed(1, 2, workRowStateOf(activity, mapOf("minecraft:log" to 42)))
        }

        assertTrue(html.contains("work-row__left\">94<"), "136 needed less 42 logged; got: $html")
        assertTrue(html.contains("of 136"), "Expected the need alongside it; got: $html")
    }

    @Test
    fun `a line with no progress shows the whole need as remaining`() {
        val activity = simplePlan().activityList.first { it.item.id == "minecraft:log" }

        val html = createHTML().div {
            workRowCollapsed(1, 2, workRowStateOf(activity, emptyMap()))
        }

        assertTrue(html.contains("work-row__left\">136<"), "Expected 136 remaining; got: $html")
    }
}
