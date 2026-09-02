package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.model.resources.ResourceSource.SourceType
import app.mcorg.domain.services.ItemSourceGraphBuilder
import app.mcorg.engine.model.SourceNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Two kinds of test, for the two kinds of claim [UnitCostModel] makes.
 *
 * **Behaviour** — the selections a player recognises, pinned on small fixtures: crafting a
 * nugget from an ingot, shearing a sheep rather than killing it, blasting copper, and not
 * looting a chest for something you can make. Each of these was a real fix, and none of them
 * was pinned by anything before this file.
 *
 * **The window each constant lives in** — the calibration sweep (`cost-diagnostics sweep`,
 * against the real 1.21.4 graph) found where each value stops holding those selections, and
 * a range nobody wrote down is a range that gets nudged. `mc-engine/CLAUDE.md` asks for
 * exactly this: sweep the constant, then record what the sweep found. The assertions on
 * [EffortTable.DEFAULT] below are that record, and each names the selection that breaks
 * outside it, so a failure reads as "you lost copper blasting", not "a number changed".
 */
class UnitCostModelTest {

    // ── fixtures ────────────────────────────────────────────────────────────

    private fun item(id: String): Item = Item(id, id.substringAfterLast(':'))

    private fun source(
        type: SourceType,
        filename: String,
        inputs: List<Pair<String, Int>> = emptyList(),
        output: Pair<String, Int>,
        expectedYield: Double? = null,
    ) = ResourceSource(
        type = type,
        filename = filename,
        requiredItems = inputs.map { (id, qty) -> item(id) as MinecraftId to ResourceQuantity.ItemQuantity(qty) },
        producedItems = listOf(
            item(output.first) to (expectedYield?.let { ResourceQuantity.ExpectedYield(it) }
                ?: ResourceQuantity.ItemQuantity(output.second))
        ),
    )

    private fun model(sources: List<ResourceSource>, effort: EffortTable = EffortTable.DEFAULT) =
        UnitCostModel(ItemSourceGraphBuilder.buildFromResourceSources(sources), effort = effort)

    /** The [SourceNode] behind a source key, so a test can price a candidate that lost. */
    private fun UnitCostModel.graphSource(key: String, sources: List<ResourceSource>): SourceNode {
        val source = sources.single { "${it.type.id}:${it.filename}" == key }
        return SourceNode(source.type, source.filename)
    }

    private fun UnitCostModel.pick(itemId: String): String {
        val chosen = best(item(itemId))
        assertNotNull(chosen, "$itemId has no finite-cost source")
        return chosen.getKey()
    }

    // ── behaviour: the selections the calibration must keep ─────────────────

    @Test
    fun `iron nugget is crafted from an ingot, not smelted out of equipment`() {
        // MCO-320's acceptance criterion. One ingot makes nine nuggets, so the craft costs a
        // ninth of an ingot plus a ninth of a bench operation; smelting a pickaxe down gives
        // one nugget for a whole furnace cycle plus a whole pickaxe.
        val sources = listOf(
            source(SourceType.LootTypes.BLOCK, "blocks/iron_ore.json", output = "minecraft:raw_iron" to 1),
            source(
                SourceType.RecipeTypes.SMELTING, "iron_ingot.json",
                inputs = listOf("minecraft:raw_iron" to 1), output = "minecraft:iron_ingot" to 1
            ),
            source(
                SourceType.RecipeTypes.CRAFTING_SHAPELESS, "iron_nugget.json",
                inputs = listOf("minecraft:iron_ingot" to 1), output = "minecraft:iron_nugget" to 9
            ),
            source(
                SourceType.RecipeTypes.SMELTING, "iron_nugget_from_smelting.json",
                inputs = listOf("minecraft:iron_pickaxe" to 1), output = "minecraft:iron_nugget" to 1
            ),
            source(
                SourceType.RecipeTypes.CRAFTING_SHAPED, "iron_pickaxe.json",
                inputs = listOf("minecraft:iron_ingot" to 3), output = "minecraft:iron_pickaxe" to 1
            ),
        )

        assertEquals("minecraft:crafting_shapeless:iron_nugget.json", model(sources).pick("minecraft:iron_nugget"))

        // And it wins on cost, not because the alternative was missing: the smelt-a-pickaxe
        // route is priced, finite, and loses by a factor of ~29 (0.88 minutes a nugget against
        // 0.03). No effort value flips this one — three ingots in for one nugget out is
        // arithmetic, not calibration, which is exactly the kind of decision the old model
        // needed a hand-swept penalty to reach.
        val model = model(sources)
        val smelted = model.graphSource("minecraft:smelting:iron_nugget_from_smelting.json", sources)
        val smeltedCost = model.costOf(smelted, item("minecraft:iron_nugget"))
        assertTrue(smeltedCost < UnitCostModel.UNREACHABLE, "the smelting route must be reachable to have lost")
        assertTrue(smeltedCost > 20 * model.cost.getValue("minecraft:iron_nugget"))
    }

    @Test
    fun `wool is sheared, not cut off a dead sheep`() {
        // A shear is 12 seconds and gives 1.5 wool; killing the sheep is 30 seconds, gives one
        // wool, and costs you the sheep. Dividing by expected yield is the whole argument —
        // the shipped scorer needed a low-yield penalty to say it.
        val sources = listOf(
            source(
                SourceType.LootTypes.SHEARING, "shearing/sheep.json",
                output = "minecraft:white_wool" to 1, expectedYield = 1.5
            ),
            source(
                SourceType.LootTypes.ENTITY, "entities/sheep.json",
                output = "minecraft:white_wool" to 1
            ),
        )

        assertEquals("minecraft:shearing:shearing/sheep.json", model(sources).pick("minecraft:white_wool"))

        // And the fixture is really exercised: raise shearing past the sweep's boundary and
        // the kill wins. Without this the test could pass because the alternative was absent
        // rather than because it lost — the way MCO-317's nugget expectation passed for years.
        val expensiveShears = EffortTable.DEFAULT.with(SourceType.LootTypes.SHEARING, 1.0)
        assertEquals(
            "minecraft:entity:entities/sheep.json",
            model(sources, expensiveShears).pick("minecraft:white_wool")
        )
    }

    @Test
    fun `copper is blasted rather than smelted, because a blast furnace is twice as fast`() {
        val sources = listOf(
            source(SourceType.LootTypes.BLOCK, "blocks/copper_ore.json", output = "minecraft:raw_copper" to 1),
            source(
                SourceType.RecipeTypes.SMELTING, "copper_ingot_from_smelting.json",
                inputs = listOf("minecraft:raw_copper" to 1), output = "minecraft:copper_ingot" to 1
            ),
            source(
                SourceType.RecipeTypes.BLASTING, "copper_ingot_from_blasting.json",
                inputs = listOf("minecraft:raw_copper" to 1), output = "minecraft:copper_ingot" to 1
            ),
        )

        assertEquals("minecraft:blasting:copper_ingot_from_blasting.json", model(sources).pick("minecraft:copper_ingot"))

        val slowBlastFurnace = EffortTable.DEFAULT.with(SourceType.RecipeTypes.BLASTING, 0.30)
        assertEquals(
            "minecraft:smelting:copper_ingot_from_smelting.json",
            model(sources, slowBlastFurnace).pick("minecraft:copper_ingot"),
            "the fixture must be able to choose smelting, or the blasting expectation proves nothing"
        )
    }

    @Test
    fun `a chest is not how you get something you can craft`() {
        // fire_charge and suspicious_stew, in miniature: a chest that hands you the finished
        // item still loses to making it, because reaching the chest is ten minutes.
        val sources = listOf(
            source(SourceType.LootTypes.BLOCK, "blocks/gravel.json", output = "minecraft:flint" to 1),
            source(SourceType.LootTypes.BLOCK, "blocks/coal_ore.json", output = "minecraft:coal" to 1),
            source(
                SourceType.RecipeTypes.CRAFTING_SHAPELESS, "fire_charge.json",
                inputs = listOf("minecraft:flint" to 1, "minecraft:coal" to 1),
                output = "minecraft:fire_charge" to 3
            ),
            source(
                SourceType.LootTypes.CHEST, "chests/bastion_other.json",
                output = "minecraft:fire_charge" to 1, expectedYield = 1.5
            ),
        )

        assertEquals("minecraft:crafting_shapeless:fire_charge.json", model(sources).pick("minecraft:fire_charge"))

        // Price a chest like a block you walked to and it takes over — which is what the real
        // graph does below 5 minutes, where chests start supplying coal, iron and paper.
        val chestNextDoor = EffortTable.DEFAULT.with(SourceType.LootTypes.CHEST, 0.05)
        assertEquals(
            "minecraft:chest:chests/bastion_other.json",
            model(sources, chestNextDoor).pick("minecraft:fire_charge")
        )
    }

    @Test
    fun `equal costs are broken recipe-first, not by graph iteration order`() {
        // Two routes at exactly the same cost: the model has no opinion, so it defers to the
        // rule PlanSelector already uses. Without this, a third of the disagreements with the
        // shipped scorer on real data were noise — 53 of 158, all exact-cost ties.
        val sources = listOf(
            source(SourceType.LootTypes.BLOCK, "blocks/stone.json", output = "minecraft:stone" to 1),
            source(
                SourceType.RecipeTypes.STONECUTTING, "stone_brick_slab_from_stonecutting.json",
                inputs = listOf("minecraft:stone" to 1), output = "minecraft:stone_brick_slab" to 1
            ),
            source(
                SourceType.RecipeTypes.CRAFTING_SHAPED, "stone_brick_slab.json",
                inputs = listOf("minecraft:stone" to 1), output = "minecraft:stone_brick_slab" to 1
            ),
        )

        // Both cost 0.05 + c(stone). Recipes both, so the key decides: crafting_shaped sorts
        // before stonecutting.
        assertEquals(
            "minecraft:crafting_shaped:stone_brick_slab.json",
            model(sources).pick("minecraft:stone_brick_slab")
        )
    }

    @Test
    fun `an item whose only route is circular has no finite cost`() {
        // The thing a score cannot say. Breaking a beacon you placed is not a way to get one.
        val sources = listOf(
            source(SourceType.LootTypes.BLOCK, "blocks/beacon.json", output = "minecraft:beacon" to 1),
            source(
                SourceType.RecipeTypes.CRAFTING_SHAPED, "beacon.json",
                inputs = listOf("minecraft:nether_star" to 1), output = "minecraft:beacon" to 1
            ),
        )

        // nether_star has no source at all, so the recipe is unreachable, and the self-block
        // loot is priced as "the beacon you already have, plus the work of breaking it".
        assertEquals(null, model(sources).best(item("minecraft:beacon")))
    }

    // ── the windows the sweep measured ──────────────────────────────────────

    private val table = EffortTable.DEFAULT

    @Test
    fun `blasting never costs more than smelting`() {
        // Sweep: copper_ingot leaves the blast furnace at blasting 0.30 (smelting 0.17), and
        // also if smelting is dropped to 0.05. Game data pins both anyway — 5s against 10s.
        assertTrue(
            table.of(SourceType.RecipeTypes.BLASTING) <= table.of(SourceType.RecipeTypes.SMELTING),
            "copper_ingot stops blasting: blasting must not cost more than smelting"
        )
        assertTrue(
            table.of(SourceType.RecipeTypes.SMOKING) <= table.of(SourceType.RecipeTypes.SMELTING),
            "cooked food stops smoking: a smoker is twice as fast as a furnace"
        )
    }

    @Test
    fun `shearing stays cheap enough, and killing stays dear enough, to keep the wool fix`() {
        // Sweep: 16 wool colours go back to crafting from string at shearing 0.60, and to
        // killing sheep at entity 0.10.
        val shearing = table.of(SourceType.LootTypes.SHEARING)
        val entity = table.of(SourceType.LootTypes.ENTITY)
        assertTrue(shearing in 0.02..0.40, "wool stops being sheared outside [0.02, 0.40], got $shearing")
        assertTrue(entity >= 0.25, "killing sheep undercuts shearing them below 0.25, got $entity")
        assertTrue(entity <= 2.0, "above 2.0 mob drops lose to structure loot, got $entity")
    }

    @Test
    fun `structure loot stays a last resort without becoming unreachable`() {
        // Sweep (0.25 -> 120 minutes): below 5, chests start supplying arrows, paper, iron,
        // coal and emerald. Above 20, items that really are chest loot get pushed onto worse
        // routes (potions to fishing, splash potions to bartering). 18 items — horse armour,
        // banner patterns, music discs — have no other route at any value, so this number
        // cannot make chests go away, only decide how far up the ladder they reach.
        val chest = table.of(SourceType.LootTypes.CHEST)
        assertTrue(chest in 5.0..20.0, "chest loot outside [5, 20] either supplies staples or is never reached, got $chest")
        assertTrue(
            chest > table.of(SourceType.LootTypes.ENTITY) && chest > table.of(SourceType.LootTypes.BLOCK),
            "reaching a chest must cost more than killing a mob or mining a block"
        )
        assertTrue(
            table.of(SourceType.LootTypes.GIFT) < chest * 1.9,
            "an egg costs less from a chicken than from a chest only while gift stays under it"
        )
    }

    @Test
    fun `the two bench stations cost the same, deliberately`() {
        // Not an accident and not a tie to be broken by taste: a crafting table and a
        // stonecutter are both one shift-click at a block you placed. Raising stonecutting
        // buys agreement with the shipped scorer (89.5% -> 92.3% at 0.20) by routing stairs
        // through the 6-in-4-out recipe instead of the 1-in-1-out cut, which spends a third
        // more stone — the opposite of what a resource planner is for.
        assertEquals(
            table.of(SourceType.RecipeTypes.CRAFTING_SHAPED),
            table.of(SourceType.RecipeTypes.STONECUTTING),
            "bench work is one operation either way; if this changes, say why in the sweep"
        )
    }

    @Test
    fun `every trade profession costs the same, and the wandering trader costs more`() {
        // Nothing in the sweep distinguishes an armorer from a shepherd, so they share a
        // number. What the data *does* distinguish is trade level (cleric/5 against
        // cleric/1), which this table cannot express at all — see TRADE_MINUTES.
        val professions = listOf(
            SourceType.TradeTypes.ARMORER, SourceType.TradeTypes.BUTCHER, SourceType.TradeTypes.CARTOGRAPHER,
            SourceType.TradeTypes.CLERIC, SourceType.TradeTypes.FARMER, SourceType.TradeTypes.FISHERMAN,
            SourceType.TradeTypes.FLETCHER, SourceType.TradeTypes.LEATHERWORKER, SourceType.TradeTypes.LIBRARIAN,
            SourceType.TradeTypes.MASON, SourceType.TradeTypes.SHEPHERD, SourceType.TradeTypes.SMITH,
            SourceType.TradeTypes.TOOLSMITH, SourceType.TradeTypes.WEAPONSMITH,
        ).map { table.of(it) }

        assertEquals(1, professions.distinct().size, "one number for all fifteen professions, or a reason per profession")
        assertTrue(
            table.of(SourceType.TradeTypes.WANDERING_TRADER) > professions.first(),
            "a wandering trader has to find you; a villager you built a hall for does not"
        )
    }

    @Test
    fun `an unnamed source type is priced as ordinary work rather than free`() {
        // A new SourceType arriving from mc-data must not silently become the cheapest route
        // in the graph. The default is a minute, which is dearer than any bench or block.
        val unknown = SourceType("mcorg:something_new", "Something New")
        assertTrue(table.of(unknown) >= table.of(SourceType.LootTypes.BLOCK) * 10)
    }
}
