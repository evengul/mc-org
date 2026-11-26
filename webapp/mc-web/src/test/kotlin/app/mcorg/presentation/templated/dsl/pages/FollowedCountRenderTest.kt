package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.SourceNode
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanTarget
import app.mcorg.pipeline.resources.FollowedChests
import app.mcorg.pipeline.resources.MeasuredStock
import app.mcorg.pipeline.resources.ProjectMeasurements
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.tr
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * A storage-tracked project's rows draw no control that writes a count, and say where the count
 * comes from instead (MCO-540).
 *
 * The server refuses a typed count either way; this is the half that keeps people from trying.
 * A counter that a sweep overwrites every 30 seconds reads as a broken product.
 */
class FollowedCountRenderTest {

    private val iron = "minecraft:iron_ingot"
    private val stock = MeasuredStock(measured = 40, containerCount = 4, oldestSeenAt = Instant.now())
    private val followed = ProjectMeasurements(mapOf(iron to stock), FollowedChests(containerCount = 4))

    private fun label(measured: MeasuredStock?, followed: FollowedChests) = createHTML().div {
        followedLabel(measured, followed)
    }

    @Test
    fun `a followed count says where it is from, and where to change that`() {
        val html = label(stock, FollowedChests(4))

        assertContains(html, "from 4 chests")
        // Says why it cannot be typed, and where to change that — not only that it cannot.
        assertContains(html, "Chest Counts")
    }

    @Test
    fun `an item in none of the chests says so rather than showing a bare zero`() {
        val html = label(measured = null, followed = FollowedChests(4))

        assertContains(html, "in none of 4 chests")
    }

    @Test
    fun `a followed plan table row offers no adopt, even when the typed count disagrees`() {
        // Adopting writes `collected`, which the server refuses on a tracked project; and the
        // count already is the chests' — there is nothing to adopt.
        val item = ResourceGatheringItem(
            id = 7, projectId = 2, itemId = iron, name = "Iron Ingot", required = 512, collected = 500,
        )
        val html = createHTML().table {
            tbody { tr { planResourceRow(1, 2, item, stock, followed = FollowedChests(4)) } }
        }

        assertFalse(html.contains("/adopt"), html)
        assertContains(html, "from 4 chests")
    }

    @Test
    fun `a followed work row has no Log and a tick that writes nothing`() {
        val html = createHTML().div {
            workRowCollapsed(1, 2, workRowStateOf(ironActivity(), mapOf(iron to 40), measurements = followed))
        }

        assertFalse(html.contains(">Log<"), html)
        assertFalse(html.contains("plan/progress"), html)
        assertContains(html, "disabled")
        assertContains(html, "from 4 chests")
    }

    @Test
    fun `a followed small-job chip writes nothing`() {
        val html = createHTML().div {
            smallJobChip(1, 2, workRowStateOf(ironActivity(), mapOf(iron to 40), measurements = followed))
        }

        assertFalse(html.contains("plan/progress"), html)
        assertContains(html, "disabled")
    }

    @Test
    fun `an untracked work row keeps its Log and its tick`() {
        val html = createHTML().div {
            workRowCollapsed(
                1, 2,
                workRowStateOf(ironActivity(), mapOf(iron to 40), measurements = ProjectMeasurements(mapOf(iron to stock))),
            )
        }

        assertContains(html, ">Log<")
        assertContains(html, "plan/progress")
    }

    private fun ironActivity() = GatheringPlan(
        nodes = mapOf(
            iron to PlanNode(
                item = Item(iron, "Iron Ingot"), quantity = 512, crafts = 512, leftover = 0,
                status = PlanNodeStatus.RAW_GATHER,
                source = SourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "blocks/iron_ore.json"),
            )
        ),
        targets = listOf(PlanTarget(Item(iron, "Iron Ingot"), 512)),
    ).activityList.single()
}
