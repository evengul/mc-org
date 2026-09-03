package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.services.ItemSourceGraphBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ActivityDiagnostics] is read-only and its output is an argument, so what these pin is that the
 * argument is computed the way it claims — a tie only counts when it crosses a kind of work, and
 * a group is only removable when *every* member has somewhere else to go.
 *
 * The measurement it produced against real data is in MCO-493: on 1.21.4, **0 of 90** whole-graph
 * ties and 0 of 8 on the YAMS project cross a group boundary, so the activity-aware tie-break has
 * nothing to act on. These tests exist so that result stays falsifiable rather than remembered.
 */
class ActivityDiagnosticsTest {

    private val crafting = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED
    private val stonecutting = ResourceSource.SourceType.RecipeTypes.STONECUTTING
    private val block = ResourceSource.SourceType.LootTypes.BLOCK
    private val entity = ResourceSource.SourceType.LootTypes.ENTITY

    private fun item(name: String) = Item("minecraft:$name", name)

    /**
     * @param yield expected output per attempt. Needed because a cross-group tie cannot be built
     *   from source types alone — `ENTITY` costs 0.5 minutes an attempt and `BLOCK` 0.05, so two
     *   one-for-one routes in different groups are never equal. Dividing by yield is what makes
     *   them meet, which is also how the only real ones would arise.
     */
    private fun source(
        type: ResourceSource.SourceType,
        filename: String,
        produces: Pair<Item, Int>,
        vararg requires: Pair<Item, Int>,
        `yield`: Double? = null,
    ) = ResourceSource(
        type = type,
        filename = filename,
        requiredItems = requires.map { (i, q) -> i to ResourceQuantity.ItemQuantity(q) },
        producedItems = listOf(
            produces.first to (
                `yield`?.let { ResourceQuantity.ExpectedYield(it) }
                    ?: ResourceQuantity.ItemQuantity(produces.second)
                ),
        ),
    )

    private fun report(sources: List<ResourceSource>, items: List<MinecraftId>) =
        ItemSourceGraphBuilder.buildFromResourceSources(sources).let { graph ->
            ActivityDiagnostics.report(graph, UnitCostModel(graph), items, "test")
        }

    /**
     * The shape MCO-493 assumed was common: two equal-cost routes that are different kinds of
     * work. Measured on real data it is not common — it is absent — but the diagnostic has to be
     * able to see one, or the zero it reports would be meaningless.
     */
    @Test
    fun `a tie across two kinds of work is counted, and makes its group removable`() {
        val trinket = item("trinket")
        val ore = item("ore")
        // Crafting and block-breaking are both 0.05 a go, so a no-ingredient recipe ties with a
        // block drop — and `best` breaks a tie recipe-first, so the trinket lands in CRAFT.
        val sources = listOf(
            source(crafting, "trinket.json", trinket to 1),
            source(block, "blocks/trinket_ore.json", trinket to 1),
            source(block, "blocks/ore.json", ore to 1),
        )

        val r = report(sources, listOf(trinket, ore))

        assertEquals(1, r.ties, "the trinket has two equal-cost routes")
        assertEquals(1, r.tiesAcrossGroups, "and they are CRAFT and GATHER")
        assertEquals(2, r.before, "so the plan asks for two kinds of work")

        val craft = r.groups.single { it.group == ActivityGroup.CRAFT }
        assertEquals(0.0, craft.exitCost!!, 1e-9, "leaving costs nothing, because it is a tie")
        assertTrue(craft.removable)

        // GATHER survives because `ore` needs it, which is what gives the trinket somewhere to go.
        assertEquals(listOf(ActivityGroup.CRAFT), r.removed)
        assertEquals(1, r.after, "one errand instead of two, at no extra cost")
    }

    /**
     * Emptying a group into one the plan does not otherwise need is not a saving — it trades one
     * errand for another. [ActivityDiagnostics.GroupReport.removable] is the per-group half of the
     * question and says yes here; [ActivityDiagnostics.ScopeReport.removed] applies the second
     * condition and correctly says no.
     */
    @Test
    fun `a group is not removed when the only destination is a group nothing else needs`() {
        val trinket = item("trinket")
        val sources = listOf(
            source(crafting, "trinket.json", trinket to 1),
            source(block, "blocks/trinket_ore.json", trinket to 1),
        )

        val r = report(sources, listOf(trinket))

        assertEquals(1, r.tiesAcrossGroups)
        assertTrue(r.groups.single().removable, "the item itself could move")
        assertTrue(r.removed.isEmpty(), "but moving it would just rename the errand")
        assertEquals(r.before, r.after)
    }

    /**
     * The case that actually occurs on real data, and the reason the answer was zero: equal costs
     * come from arithmetically identical routes, and those are almost always the same *type* of
     * source — so they land in the same group and cannot remove an errand.
     *
     * `chiseled_*` blocks are the real instance: crafting from two slabs and cutting one brick are
     * the same stone and the same click, and both are CRAFT.
     */
    @Test
    fun `a tie within one kind of work is a tie but not an errand`() {
        val chiseled = item("chiseled_stone_bricks")
        val brick = item("stone_bricks")
        val sources = listOf(
            source(block, "blocks/stone_bricks.json", brick to 1),
            source(crafting, "chiseled_from_slabs.json", chiseled to 1, brick to 1),
            source(stonecutting, "chiseled_from_bricks.json", chiseled to 1, brick to 1),
        )

        val r = report(sources, listOf(chiseled, brick))

        assertEquals(1, r.ties, "the two recipes cost the same")
        assertEquals(0, r.tiesAcrossGroups, "but crafting and stonecutting are both CRAFT")
        assertTrue(r.removed.isEmpty(), "so no kind of work can be removed")
        assertEquals(r.before, r.after)
    }

    /**
     * A group only goes when *every* member can leave. One stuck item keeps the errand, which is
     * the whole reason the greedy pass tracks membership rather than counting escapable items.
     */
    @Test
    fun `one item with nowhere to go keeps its whole group`() {
        val movable = item("movable")
        val stuck = item("stuck")
        val ore = item("ore")
        val sources = listOf(
            // Tied between crafting and a block drop, so it is in CRAFT with a way out.
            source(crafting, "movable.json", movable to 1),
            source(block, "blocks/movable_ore.json", movable to 1),
            // Craftable only, and its ingredient does not change where *it* sits.
            source(crafting, "stuck.json", stuck to 1, ore to 1),
            source(block, "blocks/ore.json", ore to 1),
        )

        val r = report(sources, listOf(movable, stuck, ore))

        val craft = r.groups.single { it.group == ActivityGroup.CRAFT }
        assertEquals(listOf("minecraft:movable", "minecraft:stuck"), craft.items)
        assertEquals(listOf("minecraft:movable"), craft.escapable, "only one of the two can leave")
        assertTrue(!craft.removable, "so CRAFT stays")
        assertTrue(r.removed.isEmpty())
        assertEquals(r.before, r.after)
    }

    /** The exit price is a cost delta in minutes, and it is zero when the alternative ties. */
    @Test
    fun `exit cost is the extra minutes the cheapest other kind of work would take`() {
        val gem = item("gem")
        val sources = listOf(
            source(block, "blocks/gem.json", gem to 1),
            // One kill yields one gem; ENTITY effort is dearer than BLOCK, so leaving costs.
            source(entity, "entities/gem.json", gem to 1),
        )

        val r = report(sources, listOf(gem))

        val gather = r.groups.single { it.group == ActivityGroup.GATHER }
        val expected = EffortTable.DEFAULT.of(entity) - EffortTable.DEFAULT.of(block)
        assertEquals(expected, gather.exitCost!!, 1e-9, "leaving GATHER means killing instead")
    }

    // ── stability(): the cheap half the calibration sweep reads (MCO-520) ───────────────────

    private fun stability(sources: List<ResourceSource>, items: List<MinecraftId>) =
        ItemSourceGraphBuilder.buildFromResourceSources(sources).let { graph ->
            ActivityDiagnostics.stability(UnitCostModel(graph), items)
        }

    /**
     * The invariant that matters: the sweep's per-row numbers and the activity report's must be
     * the same numbers. They are computed by different code — `stability` skips the escape prices
     * and the greedy pass — and a sweep whose tie count disagreed with the activity report would
     * be two answers to one question, which is exactly the failure MCO-520 is about.
     */
    @Test
    fun `stability agrees with report on the facts they both compute`() {
        val chiseled = item("chiseled_stone_bricks")
        val brick = item("stone_bricks")
        val gem = item("gem")
        val sources = listOf(
            source(block, "blocks/stone_bricks.json", brick to 1),
            source(crafting, "chiseled_from_slabs.json", chiseled to 1, brick to 1),
            source(stonecutting, "chiseled_from_bricks.json", chiseled to 1, brick to 1),
            source(entity, "entities/gem.json", gem to 1),
        )
        val items = listOf(chiseled, brick, gem)

        val r = report(sources, items)
        val s = stability(sources, items)

        assertEquals(r.ties, s.ties)
        assertEquals(r.before, s.kinds.size, "the same count of kinds of work")
        assertEquals(
            r.groups.map { it.group }.toSet(),
            s.kinds,
            "and the same kinds, not merely as many",
        )
    }

    /**
     * An item nothing produces must be *counted*, not priced at zero.
     *
     * This is what keeps the sweep's `Smin` column honest. Two effort tables are only comparable
     * by their summed cost while the same items are priced under both — an item falling out of
     * the priced set drops the sum by its whole cost, which reads as "the plan got cheaper" and
     * is the opposite of what happened.
     */
    @Test
    fun `an unproduced item is counted as unpriced rather than summed as free`() {
        val gem = item("gem")
        val ghost = item("ghost")
        val sources = listOf(source(block, "blocks/gem.json", gem to 1))

        val s = stability(sources, listOf(gem, ghost))

        assertEquals(1, s.unpriced)
        assertEquals(1, s.picks.size, "the ghost gets no pick")
        assertEquals(EffortTable.DEFAULT.of(block), s.totalMinutes, 1e-9, "and adds nothing to the sum")
        assertEquals(setOf(ActivityGroup.GATHER), s.kinds, "nor a kind of work")
    }

    /** The sum is over each item's own cheapest route, so a dearer alternative does not enter it. */
    @Test
    fun `total minutes sums the cheapest route per item`() {
        val gem = item("gem")
        val sources = listOf(
            source(block, "blocks/gem.json", gem to 1),
            source(entity, "entities/gem.json", gem to 1),
        )

        val s = stability(sources, listOf(gem))

        assertEquals(EffortTable.DEFAULT.of(block), s.totalMinutes, 1e-9)
        assertEquals(0, s.ties, "mining and killing are not the same price")
    }
}
