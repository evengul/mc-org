package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.services.ItemSourceGraphBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MCO-410: a question worth almost nothing is answered rather than asked.
 *
 * These pin the *rules*, not the threshold — the threshold is a product decision measured against
 * real projects and stated where it is set. What must not drift is that an assumption is only
 * made when it is genuinely small, that it resolves to the member the picker would show, that an
 * explicit answer always beats it, and that it is never written down.
 */
class TagAssumptionsTest {

    private fun item(id: String) = Item(id, id.substringAfterLast(':'))

    private fun block(name: String, produces: String) = ResourceSource(
        type = ResourceSource.SourceType.LootTypes.BLOCK,
        filename = "blocks/$name.json",
        producedItems = listOf(item(produces) to ResourceQuantity.ItemQuantity(1)),
    )

    private fun recipe(filename: String, inputs: List<Pair<Any, Int>>, output: Pair<String, Int>) =
        ResourceSource(
            type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
            filename = filename,
            requiredItems = inputs.map { (i, q) ->
                @Suppress("UNCHECKED_CAST")
                (i as app.mcorg.domain.model.minecraft.MinecraftId) to ResourceQuantity.ItemQuantity(q)
            },
            producedItems = listOf(item(output.first) to ResourceQuantity.ItemQuantity(output.second)),
        )

    private val sandChoice = MinecraftTag(
        "#mcorg:choice/sand", "Sand or Red Sand",
        listOf(item("minecraft:sand"), item("minecraft:red_sand")),
    )

    /**
     * One expensive target and one trivial ingredient behind a choice. `diamond` carries the
     * plan; the sand question is a rounding error on it.
     */
    private val sources = listOf(
        block("deepslate_diamond_ore", "minecraft:diamond"),
        block("sand", "minecraft:sand"),
        block("red_sand", "minecraft:red_sand"),
        recipe("tnt.json", listOf(sandChoice to 1), "minecraft:tnt" to 1),
    )

    private val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)
    private val model = UnitCostModel(graph)

    private fun planFor(diamonds: Long, tnt: Long, threshold: Double) = GatheringPlanner.plan(
        graph,
        listOf(PlanTarget(item("minecraft:diamond"), diamonds), PlanTarget(item("minecraft:tnt"), tnt)),
        context = PlanContext(assumeTagBelowShare = threshold),
        costModel = model,
    )

    @Test
    fun `a question worth almost nothing is answered rather than asked`() {
        val plan = planFor(diamonds = 10_000, tnt = 1, threshold = 0.01)

        assertEquals(1, plan.assumptions.size, "the sand choice should have been assumed")
        assertEquals(sandChoice.id, plan.assumptions.single().tagId)
        assertTrue(plan.complete, "an assumed question must not leave the plan incomplete")
        assertTrue(
            plan.nodes.values.none { it.status == PlanNodeStatus.OPEN_TAG },
            "and must not still be sitting in Needs attention",
        )
    }

    @Test
    fun `the same question is asked once it is worth asking about`() {
        // Identical graph, identical question — only the size of the build changed. This is the
        // whole argument for a share rather than a count.
        val plan = planFor(diamonds = 1, tnt = 10_000, threshold = 0.01)

        assertTrue(plan.assumptions.isEmpty(), "a question that dominates the plan must be asked")
        assertTrue(plan.nodes.values.any { it.status == PlanNodeStatus.OPEN_TAG })
    }

    @Test
    fun `an assumption resolves to what the picker would have shown`() {
        // sand and red_sand cost exactly the same, so cost cannot decide and MemberPrior does.
        // Without it the id sort would pick red_sand, and nobody would ever see that it had.
        val ranked = TagMemberRanking.rank(graph, sandChoice.content, costModel = model)
        assertEquals(ranked.first().cost, ranked.last().cost, "the fixture must be a real tie")

        val plan = planFor(diamonds = 10_000, tnt = 1, threshold = 0.01)

        assertEquals("minecraft:sand", plan.assumptions.single().memberId)
        assertEquals(
            ranked.first().member.id,
            plan.assumptions.single().memberId,
            "the assumption and the picker must not be able to disagree",
        )
    }

    @Test
    fun `an explicit answer beats an assumption`() {
        val plan = GatheringPlanner.plan(
            graph,
            listOf(
                PlanTarget(item("minecraft:diamond"), 10_000),
                PlanTarget(item("minecraft:tnt"), 1),
            ),
            overrides = PlanOverrides(tagMember = mapOf(sandChoice.id to "minecraft:red_sand")),
            context = PlanContext(assumeTagBelowShare = 0.01),
            costModel = model,
        )

        assertTrue("minecraft:red_sand" in plan.nodes, "the user's answer must stand")
        assertTrue(
            plan.assumptions.none { it.tagId == sandChoice.id },
            "an answered question must not also be reported as assumed",
        )
    }

    @Test
    fun `zero disables the mechanism entirely`() {
        val plan = planFor(diamonds = 10_000, tnt = 1, threshold = 0.0)

        assertTrue(plan.assumptions.isEmpty())
        assertTrue(plan.nodes.values.any { it.status == PlanNodeStatus.OPEN_TAG })
    }

    @Test
    fun `the reported share is the one that justified the assumption`() {
        val plan = planFor(diamonds = 10_000, tnt = 1, threshold = 0.01)
        val assumption = plan.assumptions.single()

        assertTrue(
            assumption.shareOfPlan > 0.0 && assumption.shareOfPlan < 0.01,
            "the share must be real and below the threshold, was ${assumption.shareOfPlan}",
        )
    }
}
