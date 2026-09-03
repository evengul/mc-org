package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.ItemSourceGraph
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Characterization tests for [UnitCostModel] written while trying to break it. Each one pins
 * a defect found against the real 1.21.4 graph (world 3), reduced to the smallest fixture that
 * still shows it, so that fixing the model turns the corresponding test red on purpose.
 *
 * They assert what the model **does today**, not what it should do; every one carries the
 * correct answer in its comment. That is deliberate — the point is that these behaviours are
 * currently invisible, not that they are acceptable.
 */
class UnitCostModelAdversarialTest {

    private val crafting = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED
    private val block = ResourceSource.SourceType.LootTypes.BLOCK
    private val chestLoot = ResourceSource.SourceType.LootTypes.CHEST

    private fun item(name: String) = Item("minecraft:$name", name)

    private class Fixture {
        private val builder = ItemSourceGraph.builder()
        fun source(
            type: ResourceSource.SourceType,
            filename: String,
            output: Pair<MinecraftId, Int>,
            vararg inputs: Pair<MinecraftId, Int>,
            expectedYield: Double? = null,
        ) {
            val node = builder.addSourceNode(type, filename)
            builder.addSourceToItemEdge(node, builder.addItemNode(output.first), output.second, expectedYield)
            for ((input, quantity) in inputs) {
                builder.addItemToSourceEdge(builder.addItemNode(input), node, quantity)
            }
        }

        fun build(): ItemSourceGraph = builder.build()
    }

    /**
     * **Defect 1 — a cycle whose loop gain is below 1 converges, and wins.**
     *
     * The class doc argues cycles "never improve anything and need no visiting-set guard"
     * because costs only decrease from infinity. That holds only when a source consumes at
     * least as many of the item as it produces. An armour-trim smithing template's duplication
     * recipe consumes one template and produces two, so the loop gain is 1/2 and the fixpoint
     * is finite:
     *
     *     c = (effort + 7*c(diamond) + c(deepslate) + c) / 2   ->   c = 0.45
     *
     * Against real data `silence_armor_trim_smithing_template` settles at 0.45 min and beats
     * its only real source, an ancient-city chest, by 2700x. `PlanSelector` rejects this
     * candidate structurally; the cost model has nothing that can. Nineteen items in world 3
     * are decided this way.
     *
     * The right answer here is the chest: a plan you cannot start is not cheaper than one you
     * can.
     */
    @Test
    fun `a duplication recipe is rejected rather than costed`() {
        val template = item("silence_armor_trim_smithing_template")
        val diamond = item("diamond")
        val deepslate = item("cobbled_deepslate")
        val graph = Fixture().apply {
            source(block, "blocks/diamond_ore.json", diamond to 1)
            source(block, "blocks/deepslate.json", deepslate to 1)
            // one template in, two out
            source(
                crafting, "silence_armor_trim_smithing_template.json", template to 2,
                template to 1, diamond to 7, deepslate to 1
            )
            source(chestLoot, "chests/ancient_city.json", template to 1, expectedYield = 0.0125)
        }.build()

        val model = UnitCostModel(graph)

        // The duplication recipe is not costed at all: a source requiring its own output cannot
        // ground a chain, whatever the arithmetic says about it. Before the guard this settled at
        // 0.45 min and beat the chest by ~2700x.
        assertEquals(
            "minecraft:chest:chests/ancient_city.json",
            model.best(template)?.getKey(),
            "the only real source wins, because the duplication recipe is structurally rejected"
        )
        assertTrue(
            model.cost.getValue(template.id) > 1.0,
            "and the template costs what a 1-in-80 chest drop costs, not what a loop converges to"
        )
    }

    /**
     * **Defect 2, now fixed by the same guard — `maxPasses = 64` was not "far above anything real".**
     *
     * The constructor doc says convergence needs one pass per link in the longest still-improving
     * chain and that "the deepest measured chain is 3". Against world 3 the relaxation needs
     * **67** passes and returns at 64 with `converged == false`. It happens to be within 1e-9 of
     * the fixpoint by then, so no pick moves today — but nothing checks that, and the failure is
     * silent and unbounded: the cycle above is a geometric series, and truncating it early leaves
     * a cost that is arbitrarily too high (+33320% at four passes, measured).
     */
    @Test
    fun `removing the self-consuming cycle removes the slow series that outran the pass budget`() {
        val template = item("template")
        val diamond = item("diamond")
        val graph = Fixture().apply {
            source(block, "blocks/diamond_ore.json", diamond to 1)
            source(crafting, "template.json", template to 2, template to 1, diamond to 7)
            source(chestLoot, "chests/ancient_city.json", template to 1)
        }.build()

        // Rejecting the self-consuming recipe removes the geometric series along with it, so the
        // relaxation now settles in a handful of sweeps instead of the 67 it needed on world 3.
        val settled = UnitCostModel(graph, maxPasses = 4096)
        assertTrue(settled.converged, "it settles")

        val tight = UnitCostModel(graph, maxPasses = 4)
        assertTrue(tight.converged, "and it settles inside a small budget, which is the actual fix")
        assertEquals(
            settled.cost.getValue(template.id),
            tight.cost.getValue(template.id),
            1e-12,
            "a short budget no longer silently overcharges, because there is no slow series left"
        )
        // `converged` stays on the model regardless: the guard removes today's slow series, not
        // the possibility of one, and a silent non-fixpoint is the failure worth keeping a flag for.
    }

    /**
     * **Defect 3 — the self-block-loot gate suppresses genuine harvests.**
     *
     * `wheat` has exactly two constructive-looking routes in real data: harvest the crop
     * (`blocks/wheat.json`) and unpack a hay block (9 wheat from 1). The gate fires because a
     * recipe exists, so harvesting is priced as `c(wheat) + effort` and can never win; the hay
     * block is itself 9 wheat, so it cannot win either — and wheat falls through to chest loot
     * at 1.9 min a unit, with bread and everything else downstream inheriting it.
     *
     * The same shape hits `prismarine`, `sea_lantern`, `magma_block`, `blue_ice`, `packed_ice`,
     * `mud`, `mud_bricks` and `mossy_stone_bricks` — every block that both occurs naturally and
     * has a recipe. The gate's condition is "does any recipe exist", which is not the same
     * question as "is breaking this block circular".
     */
    @Test
    fun `the gate suppresses harvesting wheat because a hay-block recipe exists`() {
        val wheat = item("wheat")
        val hayBlock = item("hay_block")
        val graph = Fixture().apply {
            source(block, "blocks/wheat.json", wheat to 1)
            source(crafting, "wheat.json", wheat to 9, hayBlock to 1)
            source(crafting, "hay_block.json", hayBlock to 1, wheat to 9)
            source(chestLoot, "chests/shipwreck_supply.json", wheat to 1, expectedYield = 7.8542)
        }.build()

        val model = UnitCostModel(graph)

        assertEquals(
            "minecraft:chest:chests/shipwreck_supply.json",
            model.best(wheat)?.getKey(),
            "harvesting a wheat crop is banned, so wheat comes from shipwrecks"
        )
        assertTrue(model.cost.getValue(wheat.id) > 1.0, "at 1.9 min a unit, against 0.05 for the harvest")
    }

    /**
     * **Defect 4 — "breaking what you placed can never win" only holds for an exact name match.**
     *
     * [SelectionScorer.isSelfBlockLoot] compares the loot-table stem to the item id, so it sees
     * `blocks/obsidian.json` for obsidian but not `blocks/ender_chest.json`, which also drops
     * obsidian — eight of it. An ender chest is 8 obsidian plus an eye of ender, so the model
     * prices obsidian at one eighth of a block-break: 0.01 min, against 0.05 for mining it and
     * 1.7 min for the chest it comes out of.
     *
     * The class doc's pack/unpack argument ("the block costs effort + 9*c(ingot), so the route
     * is always dearer") assumes the packed form has no terminal of its own. Every placed block's
     * loot table is exactly such a terminal. `book` from `blocks/bookshelf.json` (0.02 min),
     * `charcoal` from a campfire, `prismarine_crystals` from a sea lantern and `bone_meal` from
     * a composter all fail the same way.
     */
    @Test
    fun `breaking a crafted block undercuts the item it was made from`() {
        val obsidian = item("obsidian")
        val enderChest = item("ender_chest")
        val eye = item("ender_eye")
        val graph = Fixture().apply {
            source(block, "blocks/obsidian.json", obsidian to 1)
            source(chestLoot, "chests/stronghold.json", eye to 1, expectedYield = 1.0)
            source(crafting, "ender_chest.json", enderChest to 1, obsidian to 8, eye to 1)
            source(block, "blocks/ender_chest.json", obsidian to 1, expectedYield = 8.0)
        }.build()

        val model = UnitCostModel(graph)

        assertEquals(
            "minecraft:block:blocks/ender_chest.json",
            model.best(obsidian)?.getKey(),
            "the model mines ender chests for obsidian"
        )
        assertTrue(
            model.cost.getValue(obsidian.id) < model.cost.getValue(enderChest.id) / 8,
            "and so prices obsidian below one eighth of the chest that contains eight of it"
        )
    }

    /**
     * **Defect 5, now fixed — `best` declares a tie-break.**
     *
     * Filed as a defect and repaired in the same session. `minByOrNull` used to return the first
     * minimum in `getSourcesForItem`'s iteration order, which comes from the graph build order,
     * which comes from `resource_source.id`. 91 items in world 3 tie for cheapest, and about a
     * third of the reported disagreements with the shipped model were nothing but that — two
     * block-loot sources both at 0.05 min, picked by whichever row was ingested first. Those
     * picks moved on a re-ingest, which is why the same sweep reported 838 one day and 834 the
     * next.
     *
     * `best` now orders ties the way [PlanSelector] does — recipe first, then source key — so the
     * answer is a property of the graph rather than of the order it was loaded in. This test is
     * kept, inverted: it is the regression test for that fix, and it still asserts the thing that
     * made the defect findable, which is that the two costs really are identical.
     */
    @Test
    fun `two sources at the same cost are separated by a declared tie-break, not insertion order`() {
        val dandelion = item("dandelion")
        val forward = Fixture().apply {
            source(block, "blocks/dandelion.json", dandelion to 1)
            source(block, "blocks/short_grass.json", dandelion to 1)
        }.build()
        val reversed = Fixture().apply {
            source(block, "blocks/short_grass.json", dandelion to 1)
            source(block, "blocks/dandelion.json", dandelion to 1)
        }.build()

        assertEquals(
            UnitCostModel(forward).costOf(UnitCostModel(forward).best(dandelion)!!, dandelion),
            UnitCostModel(reversed).costOf(UnitCostModel(reversed).best(dandelion)!!, dandelion),
            1e-12,
            "the costs are identical"
        )
        assertEquals(
            UnitCostModel(forward).best(dandelion)?.getKey(),
            UnitCostModel(reversed).best(dandelion)?.getKey(),
            "and the pick no longer follows insertion order"
        )
    }
}
