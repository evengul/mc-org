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
     * **Defect 3, now fixed — the self-block-loot gate suppressed genuine harvests.**
     *
     * `wheat` has exactly two constructive-looking routes in real data: harvest the crop
     * (`blocks/wheat.json`) and unpack a hay block (9 wheat from 1). The gate fired because a
     * recipe existed, so harvesting was priced as `c(wheat) + effort` and could never win; the
     * hay block is itself 9 wheat, so it could not win either — and wheat fell through to chest
     * loot at 1.9 min a unit, with bread and everything downstream inheriting it.
     *
     * ## What the gate was actually keying on
     *
     * `isSelfBlockLoot` compared the loot-table stem to the item id *before* consulting
     * [PlacedForms], the curated table that exists to answer exactly this question. That table
     * mostly lists pairs whose names **differ** — `carrots`/`carrot`, `redstone_wire`/`redstone`
     * — because those are the ones a name comparison misses. So a pair it had an opinion about
     * never reached it when the names happened to match.
     *
     * The result was one family splitting on spelling. Measured on world 3 / 1.21.4, nine items
     * were demoted to -100 (`wheat`, `melon`, `pumpkin`, `sugar_cane`, `bamboo`, `nether_wart`,
     * `kelp`, `cactus`, `obsidian`) and five escaped untouched (`carrot`, `potato`, `beetroot`,
     * `cocoa_beans`, `sweet_berries`) — the second group only because Mojang pluralised those
     * block ids. Harvesting wheat scored -100 and harvesting carrots scored 100, for no reason
     * a player could name.
     *
     * The fix asks the table first and keeps the name match as the fallback it should always
     * have been — the table lists exceptions, and the ordinary case really is that placed
     * `minecraft:beacon` is the beacon you carried. The same change covers the rest of the
     * family this note originally named: `prismarine`, `sea_lantern`, `magma_block`, `blue_ice`,
     * `packed_ice`, `mud`, `mossy_stone_bricks`. `mud_bricks` deliberately stays penalised — it
     * appears in trail ruins as decoration, and nobody goes there to collect mud bricks.
     */
    @Test
    fun `a genuine harvest is no longer suppressed by an unpacking recipe`() {
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
            "minecraft:block:blocks/wheat.json",
            model.best(wheat)?.getKey(),
            "you harvest wheat; you do not sail to a shipwreck for it"
        )
        assertEquals(
            EffortTable.DEFAULT.of(block),
            model.cost.getValue(wheat.id),
            1e-9,
            "and it costs one harvest swing, not the 1.9 min the chest route charged",
        )
    }

    /**
     * The other half of the same fix: a pair the table calls REVERSIBLE stays circular, and a
     * pair it says nothing about still falls back to the name match.
     *
     * Without the fallback the fix would be far worse than the bug — [PlacedForms] lists only
     * exceptions, so every ordinary placed building block (a beacon, a block of concrete) would
     * stop being recognised as re-collection at once.
     */
    @Test
    fun `breaking what you placed is still circular, listed or not`() {
        val redstone = item("redstone")
        val redstoneBlock = item("redstone_block")
        val beacon = item("beacon")
        val glass = item("glass")
        val graph = Fixture().apply {
            // Named differently, and REVERSIBLE in the table.
            source(block, "blocks/redstone_wire.json", redstone to 1)
            source(block, "blocks/deepslate_redstone_ore.json", redstone to 1, expectedYield = 4.0)
            // The self-cost branch is gated on a constructive sibling existing, so the fixture
            // has to give redstone one or it proves nothing — the first draft of this test left
            // it out and the wire won on a branch that had never run.
            source(crafting, "redstone.json", redstone to 9, redstoneBlock to 1)
            source(crafting, "redstone_block.json", redstoneBlock to 1, redstone to 9)
            // Named identically, and absent from the table — the fallback's job.
            source(block, "blocks/beacon.json", beacon to 1)
            source(crafting, "beacon.json", beacon to 1, glass to 5)
            source(block, "blocks/glass.json", glass to 1)
        }.build()

        assertTrue(
            SelectionScorer.isSelfBlockLoot(
                redstone, graph.getSourceNode(block.id, "blocks/redstone_wire.json")!!
            ),
            "REVERSIBLE in the table beats the differing names",
        )
        assertTrue(
            SelectionScorer.isSelfBlockLoot(
                beacon, graph.getSourceNode(block.id, "blocks/beacon.json")!!
            ),
            "and silence from the table still falls back to the name match",
        )
        assertEquals(
            "minecraft:block:blocks/deepslate_redstone_ore.json",
            UnitCostModel(graph).best(redstone)?.getKey(),
            "so redstone is mined rather than picked back up off the floor",
        )
    }

    /**
     * **Defect 4, now fixed — "breaking what you placed can never win" only held for an exact
     * name match.**
     *
     * [SelectionScorer.isSelfBlockLoot] compares the loot-table stem to the item id, so it saw
     * `blocks/obsidian.json` for obsidian but not `blocks/ender_chest.json`, which also drops
     * obsidian — eight of it. An ender chest is 8 obsidian plus an eye of ender, so the model
     * priced obsidian at one eighth of a block-break: 0.01 min, against 0.05 for mining it and
     * 1.7 min for the chest it comes out of. `book` from `blocks/bookshelf.json`, `charcoal`
     * from a campfire and `soul_soil` from a soul campfire all failed the same way.
     *
     * ## What fixed it, and what did not
     *
     * MCO-501 opened proposing a curated "does this block generate naturally?" flag, and that
     * would **not** have fixed this case: an ender chest does generate, in End city ships. So
     * would a rule banning the route, which would then have to answer for the bookshelf — where
     * breaking one you find in a village library is a perfectly ordinary thing to do.
     *
     * What was actually wrong is narrower: the route was **free**. `blocks/ender_chest.json` had
     * a finding factor of 1.0, priced as though ender chests lie about at the rate dirt does.
     * [StructureDensity] now derives from the server jar that an ender chest is placed only by
     * `end_cities`, one per 400 chunks and behind a dead dragon, so the route is priced rather
     * than forbidden — and loses on its merits, by 3.6x.
     *
     * Kept and inverted, like Defect 5 below: this is now the regression test for that pricing,
     * and it still asserts the fact that made the defect findable, which is that breaking the
     * chest yields eight obsidian for one swing.
     */
    @Test
    fun `breaking a crafted block no longer undercuts the item it was made from`() {
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
            "minecraft:block:blocks/obsidian.json",
            model.best(obsidian)?.getKey(),
            "obsidian is mined, not extracted from the chest it is an ingredient of"
        )
        assertTrue(
            model.cost.getValue(obsidian.id) <
                UnitCostModel(graph).costOf(
                    graph.getSourceNode(block.id, "blocks/ender_chest.json")!!, obsidian
                ),
            "and the End city route is dearer, rather than unavailable"
        )
    }

    /**
     * The other half of the same fix, and the one that says why it is keyed on *craftable*.
     *
     * A structure template names every block it places, which is not the same as the blocks it
     * gates. A village is built out of cobblestone, sand, dirt and oak logs, all of which are in
     * `structure-density.txt`'s membership. Pricing a block by the village that contains it is
     * only right when finding one there is an alternative to *making* it.
     *
     * Measured on world 3 when this gate was missing: cobblestone went 0.05 -> 0.43 min, sand
     * 0.05 -> 0.43, oak_log 0.05 -> 0.62, and dirt rerouted to breaking mycelium — the model
     * telling a player to find a mushroom island for dirt. Terrain has no recipe, so the gate
     * excludes exactly that population.
     */
    @Test
    fun `a structure's ordinary building blocks are not priced as structure loot`() {
        val cobblestone = item("cobblestone")
        val graph = Fixture().apply {
            source(block, "blocks/cobblestone.json", cobblestone to 1)
        }.build()

        val model = UnitCostModel(graph)

        assertEquals(
            EffortTable.DEFAULT.of(block),
            model.cost.getValue(cobblestone.id),
            1e-9,
            "cobblestone is in villages and is still just a block you break",
        )
    }

    /**
     * The shape MCO-501's structure pricing cannot reach, and why it needed its own branch.
     *
     * `soul_soil` was sourced from `blocks/soul_campfire.json` at 0.05 min. A loot source
     * consumes nothing, so the model paid nothing for the soul campfire — which costs a soul
     * sand, three sticks and three logs to make. Unlike the ender chest there is no structure to
     * price the route by: no template in the game places a soul campfire, so the only way to
     * have one is to build it, and breaking it must therefore cost building it.
     */
    @Test
    fun `breaking a block that only exists because you built it charges what it cost`() {
        val soulSoil = item("soul_soil")
        val soulCampfire = item("soul_campfire")
        val stick = item("stick")
        val graph = Fixture().apply {
            source(block, "blocks/soul_sand.json", soulSoil to 1, expectedYield = 0.1)
            source(crafting, "soul_campfire.json", soulCampfire to 1, soulSoil to 1, stick to 3)
            source(block, "blocks/stick.json", stick to 1)
            source(block, "blocks/soul_campfire.json", soulSoil to 1)
        }.build()

        val model = UnitCostModel(graph)
        val viaCampfire = model.costOf(
            graph.getSourceNode(block.id, "blocks/soul_campfire.json")!!, soulSoil
        )

        assertTrue(
            viaCampfire > model.cost.getValue(soulCampfire.id),
            "breaking the campfire costs at least the campfire, not one free block-break",
        )
        assertEquals(
            "minecraft:block:blocks/soul_sand.json",
            model.best(soulSoil)?.getKey(),
            "so even a one-in-ten drop beats dismantling something you had to build",
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
