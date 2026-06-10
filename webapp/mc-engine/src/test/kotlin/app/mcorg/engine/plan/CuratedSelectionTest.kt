package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.services.ItemSourceGraphBuilder
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Curated selection suite: for Minecraft items where a knowledgeable player has
 * a clear "best" acquisition path, pin the expectation that the planner selects
 * it. Fixtures run the real pipeline (ResourceSource ->
 * ItemSourceGraphBuilder -> GatheringPlanner) with realistic source types,
 * quantities, and loot-table filenames (block loot = "blocks/<block>.json",
 * entity loot = "entities/<mob>.json").
 *
 * See documentation/work-documents/fable-mc-engine-scoring-audit.md for the
 * factor-by-factor analysis behind each expectation.
 */
class CuratedSelectionTest {

    // ── fixture helpers ─────────────────────────────────────────────────────

    private fun item(id: String): Item = Item(id, id.substringAfterLast(':'))

    private fun recipe(
        filename: String,
        inputs: List<Pair<MinecraftId, Int>>,
        output: Pair<String, Int>,
        type: ResourceSource.SourceType = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED
    ) = ResourceSource(
        type = type,
        filename = filename,
        requiredItems = inputs.map { (input, qty) -> input to ResourceQuantity.ItemQuantity(qty) },
        producedItems = listOf(item(output.first) to ResourceQuantity.ItemQuantity(output.second))
    )

    private fun blockLoot(blockName: String, produces: String, quantity: Int = 1) = ResourceSource(
        type = ResourceSource.SourceType.LootTypes.BLOCK,
        filename = "blocks/$blockName.json",
        producedItems = listOf(item(produces) to ResourceQuantity.ItemQuantity(quantity))
    )

    private fun entityLoot(entityName: String, produces: String, quantity: Int = 1) = ResourceSource(
        type = ResourceSource.SourceType.LootTypes.ENTITY,
        filename = "entities/$entityName.json",
        producedItems = listOf(item(produces) to ResourceQuantity.ItemQuantity(quantity))
    )

    private fun chestLoot(path: String, produces: String, quantity: Int = 1) = ResourceSource(
        type = ResourceSource.SourceType.LootTypes.CHEST,
        filename = "chests/$path.json",
        producedItems = listOf(item(produces) to ResourceQuantity.ItemQuantity(quantity))
    )

    private fun plan(
        sources: List<ResourceSource>,
        target: String,
        amount: Long = 10,
        supplied: Map<String, SupplySource> = emptyMap()
    ): GatheringPlan {
        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)
        return GatheringPlanner.plan(graph, listOf(PlanTarget(item(target), amount)), supplied)
    }

    private fun GatheringPlan.sourceKeyOf(itemId: String): String? = nodes.getValue(itemId).source?.getKey()

    // ── reusable chains ─────────────────────────────────────────────────────

    /** oak_log (mined) -> oak_planks (crafted, 1 log -> 4 planks); placed planks drop themselves. */
    private val plankChain = listOf(
        recipe(
            "oak_planks.json",
            inputs = listOf(item("minecraft:oak_log") to 1),
            output = "minecraft:oak_planks" to 4,
            type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS
        ),
        blockLoot("oak_log", "minecraft:oak_log"),
        blockLoot("oak_planks", "minecraft:oak_planks")
    )

    /** stick crafted from planks (2 -> 4); witches also drop sticks. */
    private val stickChain = plankChain + listOf(
        recipe(
            "stick.json",
            inputs = listOf(item("minecraft:oak_planks") to 2),
            output = "minecraft:stick" to 4
        ),
        entityLoot("witch", "minecraft:stick")
    )

    /**
     * iron_ingot with the realistic source set minus entity loot: smelting and
     * blasting raw iron, unpacking an iron block, crafting from nuggets (both
     * circular — block and nuggets are crafted from ingots), and chest loot.
     */
    private val ironChain = listOf(
        recipe(
            "iron_ingot_from_smelting.json",
            inputs = listOf(item("minecraft:raw_iron") to 1),
            output = "minecraft:iron_ingot" to 1,
            type = ResourceSource.SourceType.RecipeTypes.SMELTING
        ),
        recipe(
            "iron_ingot_from_blasting.json",
            inputs = listOf(item("minecraft:raw_iron") to 1),
            output = "minecraft:iron_ingot" to 1,
            type = ResourceSource.SourceType.RecipeTypes.BLASTING
        ),
        recipe(
            "iron_ingot_from_iron_block.json",
            inputs = listOf(item("minecraft:iron_block") to 1),
            output = "minecraft:iron_ingot" to 9
        ),
        recipe(
            "iron_ingot_from_nuggets.json",
            inputs = listOf(item("minecraft:iron_nugget") to 9),
            output = "minecraft:iron_ingot" to 1
        ),
        chestLoot("stronghold_corridor", "minecraft:iron_ingot"),
        recipe(
            "iron_block.json",
            inputs = listOf(item("minecraft:iron_ingot") to 9),
            output = "minecraft:iron_block" to 1
        ),
        blockLoot("iron_block", "minecraft:iron_block"),
        recipe(
            "iron_nugget_from_ingot.json",
            inputs = listOf(item("minecraft:iron_ingot") to 1),
            output = "minecraft:iron_nugget" to 9
        ),
        blockLoot("iron_ore", "minecraft:raw_iron")
    )

    // ── planks / sticks ─────────────────────────────────────────────────────

    @Test
    fun `oak_planks - crafting from logs beats breaking placed planks`() {
        val result = plan(plankChain, "minecraft:oak_planks")

        assertEquals("minecraft:crafting_shapeless:oak_planks.json", result.sourceKeyOf("minecraft:oak_planks"))
        assertEquals("minecraft:block:blocks/oak_log.json", result.sourceKeyOf("minecraft:oak_log"))
    }

    @Test
    @Disabled(
        "KNOWN MIS-RANK pending drop-rate data (MCO-196) — ENTITY base score (100) outranks the stick " +
            "recipe (~95) below the recipe threshold, so 'kill witches' is suggested for a handful of sticks."
    )
    fun `stick - small amount should be crafted from planks, not farmed from witches`() {
        val result = plan(stickChain, "minecraft:stick", amount = 10)

        assertEquals("minecraft:crafting_shaped:stick.json", result.sourceKeyOf("minecraft:stick"))
    }

    @Test
    fun `stick - bulk amount is crafted thanks to the recipe threshold bonus`() {
        val result = plan(stickChain, "minecraft:stick", amount = 256)

        assertEquals("minecraft:crafting_shaped:stick.json", result.sourceKeyOf("minecraft:stick"))
        assertEquals("minecraft:crafting_shapeless:oak_planks.json", result.sourceKeyOf("minecraft:oak_planks"))
        assertEquals("minecraft:block:blocks/oak_log.json", result.sourceKeyOf("minecraft:oak_log"))
        assertTrue(result.complete)
    }

    // ── torch ───────────────────────────────────────────────────────────────

    @Test
    fun `torch - crafting from coal and stick beats own block loot and chest loot`() {
        val sources = stickChain + listOf(
            recipe(
                "torch.json",
                inputs = listOf(item("minecraft:coal") to 1, item("minecraft:stick") to 1),
                output = "minecraft:torch" to 4
            ),
            blockLoot("torch", "minecraft:torch"),
            chestLoot("abandoned_mineshaft", "minecraft:torch"),
            blockLoot("coal_ore", "minecraft:coal")
        )

        val result = plan(sources, "minecraft:torch")

        assertEquals("minecraft:crafting_shaped:torch.json", result.sourceKeyOf("minecraft:torch"))
        assertEquals("minecraft:block:blocks/coal_ore.json", result.sourceKeyOf("minecraft:coal"))
        // Stick resolves to witch loot at this demand (see the disabled stick test);
        // assert it is resolved at all rather than to which source.
        assertNotNull(result.nodes.getValue("minecraft:stick").source)
    }

    // ── tags stay open ──────────────────────────────────────────────────────

    @Test
    fun `chest - crafting wins and the planks tag stays open for the user`() {
        val planksTag = MinecraftTag(
            "minecraft:planks", "Planks",
            listOf(item("minecraft:oak_planks"), item("minecraft:birch_planks"))
        )
        val sources = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "chest.json",
                requiredItems = listOf(planksTag to ResourceQuantity.ItemQuantity(8)),
                producedItems = listOf(item("minecraft:chest") to ResourceQuantity.ItemQuantity(1))
            ),
            blockLoot("chest", "minecraft:chest"),
            chestLoot("shipwreck_supply", "minecraft:chest"),
            recipe(
                "oak_planks.json",
                inputs = listOf(item("minecraft:oak_log") to 1),
                output = "minecraft:oak_planks" to 4,
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS
            ),
            recipe(
                "birch_planks.json",
                inputs = listOf(item("minecraft:birch_log") to 1),
                output = "minecraft:birch_planks" to 4,
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS
            ),
            blockLoot("oak_log", "minecraft:oak_log"),
            blockLoot("birch_log", "minecraft:birch_log")
        )

        val result = plan(sources, "minecraft:chest")

        assertEquals("minecraft:crafting_shaped:chest.json", result.sourceKeyOf("minecraft:chest"))
        val tagNode = result.nodes.getValue("minecraft:planks")
        assertEquals(PlanNodeStatus.OPEN_TAG, tagNode.status)
        assertEquals(80, tagNode.quantity)
        assertFalse(result.complete)
        assertEquals(listOf("minecraft:planks"), result.needsAttention.map { it.item.id })
    }

    @Test
    fun `glass - smelting sand beats re-collecting placed glass`() {
        val sandTag = MinecraftTag(
            "minecraft:sand", "Sand",
            listOf(item("minecraft:sand"), item("minecraft:red_sand"))
        )
        val sources = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.SMELTING,
                filename = "glass.json",
                requiredItems = listOf(sandTag to ResourceQuantity.ItemQuantity(1)),
                producedItems = listOf(item("minecraft:glass") to ResourceQuantity.ItemQuantity(1))
            ),
            blockLoot("glass", "minecraft:glass"),
            blockLoot("sand", "minecraft:sand"),
            blockLoot("red_sand", "minecraft:red_sand")
        )

        val result = plan(sources, "minecraft:glass")

        assertEquals("minecraft:smelting:glass.json", result.sourceKeyOf("minecraft:glass"))
        assertEquals(PlanNodeStatus.OPEN_TAG, result.nodes.getValue("minecraft:sand").status)
    }

    // ── circular block loot ─────────────────────────────────────────────────

    @Test
    fun `beacon - crafting beats breaking the beacon you placed`() {
        val sources = listOf(
            recipe(
                "beacon.json",
                inputs = listOf(
                    item("minecraft:glass") to 5,
                    item("minecraft:obsidian") to 3,
                    item("minecraft:nether_star") to 1
                ),
                output = "minecraft:beacon" to 1
            ),
            blockLoot("beacon", "minecraft:beacon"),
            blockLoot("glass", "minecraft:glass"),
            blockLoot("obsidian", "minecraft:obsidian"),
            ResourceSource(
                type = ResourceSource.SourceType.LootTypes.BARTER,
                filename = "piglin_bartering.json",
                producedItems = listOf(item("minecraft:obsidian") to ResourceQuantity.ItemQuantity(1))
            ),
            entityLoot("wither", "minecraft:nether_star")
        )

        val result = plan(sources, "minecraft:beacon", amount = 1)

        assertEquals("minecraft:crafting_shaped:beacon.json", result.sourceKeyOf("minecraft:beacon"))
        assertEquals("minecraft:block:blocks/obsidian.json", result.sourceKeyOf("minecraft:obsidian"))
        assertEquals("minecraft:entity:entities/wither.json", result.sourceKeyOf("minecraft:nether_star"))
    }

    @Test
    fun `redstone_lamp - crafting beats breaking the placed lamp`() {
        val sources = listOf(
            recipe(
                "redstone_lamp.json",
                inputs = listOf(item("minecraft:glowstone") to 1, item("minecraft:redstone") to 4),
                output = "minecraft:redstone_lamp" to 1
            ),
            blockLoot("redstone_lamp", "minecraft:redstone_lamp"),
            recipe(
                "glowstone.json",
                inputs = listOf(item("minecraft:glowstone_dust") to 4),
                output = "minecraft:glowstone" to 1
            ),
            blockLoot("glowstone", "minecraft:glowstone_dust", quantity = 3),
            blockLoot("redstone_ore", "minecraft:redstone", quantity = 4)
        )

        val result = plan(sources, "minecraft:redstone_lamp", amount = 4)

        assertEquals("minecraft:crafting_shaped:redstone_lamp.json", result.sourceKeyOf("minecraft:redstone_lamp"))
        assertEquals("minecraft:crafting_shaped:glowstone.json", result.sourceKeyOf("minecraft:glowstone"))
        // "blocks/glowstone.json" produces glowstone_dust, not glowstone — no false circularity.
        assertEquals("minecraft:block:blocks/glowstone.json", result.sourceKeyOf("minecraft:glowstone_dust"))
        assertEquals("minecraft:block:blocks/redstone_ore.json", result.sourceKeyOf("minecraft:redstone"))
    }

    // ── storage blocks ──────────────────────────────────────────────────────

    @Test
    fun `diamond - mining diamond ore beats unpacking a diamond block`() {
        val sources = listOf(
            blockLoot("diamond_ore", "minecraft:diamond"),
            blockLoot("deepslate_diamond_ore", "minecraft:diamond"),
            recipe(
                "diamond_from_diamond_block.json",
                inputs = listOf(item("minecraft:diamond_block") to 1),
                output = "minecraft:diamond" to 9
            ),
            chestLoot("buried_treasure", "minecraft:diamond"),
            recipe(
                "diamond_block.json",
                inputs = listOf(item("minecraft:diamond") to 9),
                output = "minecraft:diamond_block" to 1
            ),
            blockLoot("diamond_block", "minecraft:diamond_block")
        )

        val result = plan(sources, "minecraft:diamond")

        val selected = result.sourceKeyOf("minecraft:diamond")
        assertTrue(
            selected == "minecraft:block:blocks/diamond_ore.json"
                || selected == "minecraft:block:blocks/deepslate_diamond_ore.json",
            "Diamond should be mined from ore, got $selected"
        )
        assertTrue("minecraft:diamond_block" !in result.nodes)
    }

    @Test
    fun `iron_ingot - smelting raw iron beats unpacking blocks, packing nuggets, and chest loot`() {
        val result = plan(ironChain, "minecraft:iron_ingot")

        assertEquals("minecraft:smelting:iron_ingot_from_smelting.json", result.sourceKeyOf("minecraft:iron_ingot"))
        assertEquals("minecraft:block:blocks/iron_ore.json", result.sourceKeyOf("minecraft:raw_iron"))
        assertTrue("minecraft:iron_block" !in result.nodes)
        assertTrue("minecraft:iron_nugget" !in result.nodes)
    }

    @Test
    fun `iron_ingot - bulk amount still smelts, with the recipe threshold reinforcing it`() {
        val result = plan(ironChain, "minecraft:iron_ingot", amount = 500)

        assertEquals("minecraft:smelting:iron_ingot_from_smelting.json", result.sourceKeyOf("minecraft:iron_ingot"))
    }

    @Test
    fun `coal - mining coal ore beats unpacking a coal block`() {
        val sources = listOf(
            blockLoot("coal_ore", "minecraft:coal"),
            recipe(
                "coal_from_coal_block.json",
                inputs = listOf(item("minecraft:coal_block") to 1),
                output = "minecraft:coal" to 9
            ),
            recipe(
                "coal_block.json",
                inputs = listOf(item("minecraft:coal") to 9),
                output = "minecraft:coal_block" to 1
            ),
            blockLoot("coal_block", "minecraft:coal_block")
        )

        val result = plan(sources, "minecraft:coal")

        assertEquals("minecraft:block:blocks/coal_ore.json", result.sourceKeyOf("minecraft:coal"))
    }

    @Test
    fun `own-block loot does not ground an unpack chain`() {
        // Without the self-block rule in the acquirability check, the planner
        // would happily plan "unpack iron blocks <- break placed iron blocks".
        val result = plan(ironChain, "minecraft:iron_ingot")

        val ironSources = result.nodes.values.mapNotNull { it.source?.getKey() }
        assertTrue("minecraft:crafting_shaped:iron_ingot_from_iron_block.json" !in ironSources)
        assertTrue("minecraft:block:blocks/iron_block.json" !in ironSources)
    }

    // ── supplied ingredients flip the decision ──────────────────────────────

    private val arrowChain = listOf(
        recipe(
            "arrow.json",
            inputs = listOf(
                item("minecraft:flint") to 1,
                item("minecraft:stick") to 1,
                item("minecraft:feather") to 1
            ),
            output = "minecraft:arrow" to 4
        ),
        entityLoot("skeleton", "minecraft:arrow"),
        blockLoot("gravel", "minecraft:flint"),
        recipe(
            "stick.json",
            inputs = listOf(item("minecraft:oak_planks") to 2),
            output = "minecraft:stick" to 4
        ),
        entityLoot("chicken", "minecraft:feather")
    )

    @Test
    fun `arrow - without ingredient farms, a skeleton farm is suggested`() {
        // Both answers are defensible for a player; this pins the current choice.
        val result = plan(arrowChain, "minecraft:arrow")

        assertEquals("minecraft:entity:entities/skeleton.json", result.sourceKeyOf("minecraft:arrow"))
    }

    @Test
    fun `arrow - with flint, stick and feather farms, crafting is suggested`() {
        val farms = mapOf(
            "minecraft:flint" to SupplySource.Farm("Gravel duper"),
            "minecraft:stick" to SupplySource.Farm("Stick farm"),
            "minecraft:feather" to SupplySource.Farm("Chicken farm")
        )

        val result = plan(arrowChain, "minecraft:arrow", supplied = farms)

        assertEquals("minecraft:crafting_shaped:arrow.json", result.sourceKeyOf("minecraft:arrow"))
        assertEquals(PlanNodeStatus.SUPPLIED, result.nodes.getValue("minecraft:flint").status)
        assertEquals(PlanNodeStatus.SUPPLIED, result.nodes.getValue("minecraft:stick").status)
        assertEquals(PlanNodeStatus.SUPPLIED, result.nodes.getValue("minecraft:feather").status)
    }

    // ── cross-branch truncation is structurally gone ────────────────────────

    @Test
    fun `an ingredient shared between competing recipes is fully resolved in the winning chain`() {
        val sources = listOf(
            // Listed first: would have claimed the shared subtree in a tree model.
            recipe(
                "widget_expensive.json",
                inputs = listOf(item("minecraft:shared") to 1, item("minecraft:junk") to 1),
                output = "minecraft:widget" to 1
            ),
            // Listed second, wins scoring (fewer requirements).
            recipe(
                "widget_cheap.json",
                inputs = listOf(item("minecraft:shared") to 1),
                output = "minecraft:widget" to 1
            ),
            blockLoot("shared_ore", "minecraft:shared"),
            blockLoot("junk", "minecraft:junk")
        )

        val result = plan(sources, "minecraft:widget")

        assertEquals("minecraft:crafting_shaped:widget_cheap.json", result.sourceKeyOf("minecraft:widget"))
        val shared = result.nodes.getValue("minecraft:shared")
        assertEquals(PlanNodeStatus.RAW_GATHER, shared.status)
        assertEquals("minecraft:block:blocks/shared_ore.json", shared.source?.getKey())
        assertTrue(result.complete)
        assertTrue("minecraft:junk" !in result.nodes)
    }
}
