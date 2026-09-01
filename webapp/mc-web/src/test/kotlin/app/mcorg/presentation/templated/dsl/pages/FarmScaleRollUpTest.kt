package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.model.world.World
import app.mcorg.engine.model.SourceNode
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
    private val water = Item("minecraft:water", "Water")
    private val lava = Item("minecraft:lava", "Lava")

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
        source: SourceNode? = null,
    ) = PlanNode(
        item = item,
        quantity = quantity,
        crafts = 0,
        leftover = 0,
        status = status,
        source = source,
        supply = supply,
    )

    /** Fill a bucket from the world — water, lava (MCO-467). */
    private fun collect(filename: String) =
        SourceNode(ResourceSource.SourceType.MechanicTypes.COLLECT, filename)

    /** Break a block for its drop — how ice is actually obtained, and why ice stays farmable. */
    private fun breakBlock(filename: String) =
        SourceNode(ResourceSource.SourceType.LootTypes.BLOCK, filename)

    private fun render(
        plan: GatheringPlan,
        threshold: Int = World.DEFAULT_FARM_SCALE_THRESHOLD,
        isWorldAdmin: Boolean = true,
    ) = gatheringPlannerFragment(
        project = project(),
        resources = emptyList(),
        tasks = emptyList(),
        plan = plan,
        farmScaleThreshold = threshold,
        isWorldAdmin = isWorldAdmin,
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

        // The threshold sits in its own anchor, so assert the prose and the number separately.
        assertContains(html, "2 raw materials need more than ")
        assertContains(html, ">1,728<")
    }

    @Test
    fun `one material reads in the singular`() {
        val html = render(plan(node(ice, 20_611)))

        assertContains(html, "1 raw material needs more than ")
        assertContains(html, ">1,728<")
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
        // Scoped to the roll-up's own cell. A bare `contains("32,967")` used to stand in for
        // this and stopped meaning it in MCO-403, which prints the demand on the supplied row
        // itself — the number is now on the page on purpose, just not in the roll-up.
        assertFalse(html.contains("""<span class="plan-farm-scale__quantity">32,967</span>"""))
    }

    /**
     * MCO-467 — water is unbounded at the source. On the YAMS import it cleared the threshold at
     * 2,413 and led the roll-up, offering a farm that cannot exist.
     */
    @Test
    fun `a material you fill from the world is never farm-scale`() {
        val html = render(
            plan(
                node(cobblestone, 74_557),
                node(water, 2_413, source = collect("synthetic/water.json")),
                node(lava, 9_999, source = collect("synthetic/lava.json")),
            )
        )

        assertContains(html, "1 raw material needs")
        assertFalse(html.contains("""<span class="plan-farm-scale__quantity">2,413</span>"""))
        assertFalse(html.contains("""<span class="plan-farm-scale__quantity">9,999</span>"""))
    }

    /**
     * The other side of the same line. Ice is broken out of the world, is genuinely farmable,
     * and two ice farm designs sit in the bank — a rule that caught it would be worse than the
     * bug it fixed.
     */
    @Test
    fun `a material you break out of the world is still farm-scale`() {
        val html = render(plan(node(ice, 20_611, source = breakBlock("blocks/ice.json"))))

        assertContains(html, "1 raw material needs")
        assertContains(html, """<span class="plan-farm-scale__quantity">20,611</span>""")
    }

    /** A plan whose only bulk demand is water has no farm question left to ask. */
    @Test
    fun `water alone produces no roll-up`() {
        val html = render(plan(node(water, 2_413, source = collect("synthetic/water.json"))))

        assertFalse(html.contains("plan-farm-scale"))
        assertFalse(html.contains("Worth a farm"))
    }

    @Test
    fun `farm-scale rows carry the badge and others do not`() {
        val html = render(plan(node(cobblestone, 74_557), node(ice, 12)))

        assertContains(html, "plan-farm-scale__badge")
        // One badge, for the one row over the line — not one per raw row.
        assertTrue(html.split("plan-farm-scale__badge").size - 1 == 1)
    }

    @Test
    fun `an admin can click the threshold through to world settings`() {
        // The number is the judgement the list rests on, so it is the thing to edit.
        val html = render(plan(node(cobblestone, 74_557)))

        assertContains(html, """href="/worlds/1/settings"""")
        assertContains(html, "plan-farm-scale__threshold")
    }

    @Test
    fun `a non-admin sees the threshold as plain text`() {
        // World settings is admin-gated, and a link that 403s is worse than no link.
        val html = render(plan(node(cobblestone, 74_557)), isWorldAdmin = false)

        assertContains(html, "1,728")
        assertFalse(html.contains("plan-farm-scale__threshold"))
        assertFalse(html.contains("/worlds/1/settings"))
    }

    @Test
    fun `raising the world threshold shrinks the roll-up`() {
        val plan = plan(node(cobblestone, 74_557), node(ice, 20_611))

        val html = render(plan, threshold = 50_000)

        assertContains(html, "1 raw material needs more than ")
        assertContains(html, ">50,000<")
        assertFalse(html.contains("20,611"))
    }
}
