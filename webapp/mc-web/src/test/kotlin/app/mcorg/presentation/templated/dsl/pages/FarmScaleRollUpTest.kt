package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.world.World
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.engine.plan.PlanNode
import app.mcorg.engine.plan.PlanNodeStatus
import app.mcorg.engine.plan.PlanTarget
import app.mcorg.engine.plan.SupplySource
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MCO-401 — the "Worth a farm" roll-up as rendered.
 *
 * Rendered rather than integration-tested end to end: the roll-up only appears once a plan has
 * derived, and deriving one needs an ingested item graph that no test fixture provides (the
 * existing GatheringPlannerIT asserts page structure against a null plan for the same reason).
 * This drives the real template with a real plan, so everything but the DB read is covered;
 * the threshold's own storage is covered by WorldSettingsIT.
 */
class FarmScaleRollUpTest {

    private val cobblestone = Item("minecraft:cobblestone", "Cobblestone")
    private val ice = Item("minecraft:ice", "Ice")
    private val ironIngot = Item("minecraft:iron_ingot", "Iron Ingot")
    private val torch = Item("minecraft:torch", "Torch")

    private fun project() = Project(
        id = 2,
        worldId = 1,
        name = "Storage System",
        description = "",
        type = ProjectType.BUILDING,
        stage = ProjectStage.PLANNING,
        state = ProjectState.ACTIVE,
        location = null,
        tasksTotal = 0,
        tasksCompleted = 0,
        importedFromIdea = null,
        createdAt = ZonedDateTime.now(),
        updatedAt = ZonedDateTime.now(),
    )

    private fun plan(vararg nodes: PlanNode) = GatheringPlan(
        nodes = nodes.associateBy { it.item.id },
        targets = nodes.map { PlanTarget(it.item, it.quantity) },
    )

    private fun node(
        item: Item,
        quantity: Long,
        status: PlanNodeStatus = PlanNodeStatus.RAW_GATHER,
        supply: SupplySource? = null,
    ) = PlanNode(item = item, quantity = quantity, crafts = 0, leftover = 0, status = status, supply = supply)

    private fun render(plan: GatheringPlan, threshold: Int = World.DEFAULT_FARM_SCALE_THRESHOLD) =
        gatheringPlannerFragment(
            project = project(),
            resources = emptyList(),
            tasks = emptyList(),
            plan = plan,
            farmScaleThreshold = threshold,
        )

    @Test
    fun `bulk raw demand is rolled up largest first`() {
        val html = render(plan(node(ice, 20_611), node(cobblestone, 74_557)))

        assertContains(html, "Worth a farm")
        assertContains(html, "74,557")
        assertContains(html, "20,611")
        // The ordering is the message — the top line is where the roadmap starts.
        assertTrue(html.indexOf("74,557") < html.indexOf("20,611"))
    }

    @Test
    fun `the lead line counts the materials and names the threshold`() {
        val html = render(plan(node(ice, 20_611), node(cobblestone, 74_557)))

        assertContains(html, "2 raw materials need more than 1,728")
    }

    @Test
    fun `one material reads in the singular`() {
        val html = render(plan(node(ice, 20_611)))

        assertContains(html, "1 raw material needs more than 1,728")
    }

    @Test
    fun `a plan with nothing farm-scale shows no roll-up at all`() {
        // Not an empty panel: a plan under the threshold has no farm question to answer, and a
        // heading with nothing under it reads as a bug.
        val html = render(plan(node(torch, 64), node(ice, 12)))

        assertFalse(html.contains("plan-farm-scale"))
        assertFalse(html.contains("Worth a farm"))
    }

    @Test
    fun `an item an operational farm already supplies stays out of the roll-up`() {
        val html = render(
            plan(
                node(cobblestone, 74_557),
                node(ironIngot, 32_967, PlanNodeStatus.SUPPLIED, SupplySource.Farm("Iron Farm")),
            )
        )

        assertContains(html, "1 raw material needs")
        assertFalse(html.contains("32,967"))
    }

    @Test
    fun `farm-scale rows carry the badge and others do not`() {
        val html = render(plan(node(cobblestone, 74_557), node(ice, 12)))

        assertContains(html, "plan-farm-scale__badge")
        // One badge, for the one row over the line — not one per raw row.
        assertTrue(html.split("plan-farm-scale__badge").size - 1 == 1)
    }

    @Test
    fun `raising the world threshold shrinks the roll-up`() {
        val plan = plan(node(cobblestone, 74_557), node(ice, 20_611))

        val html = render(plan, threshold = 50_000)

        assertContains(html, "1 raw material needs more than 50,000")
        assertFalse(html.contains("20,611"))
    }
}
