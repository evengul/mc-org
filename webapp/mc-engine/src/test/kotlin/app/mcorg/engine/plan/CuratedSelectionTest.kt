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

    private fun entityLoot(
        entityName: String,
        produces: String,
        quantity: Int = 1,
        expectedYield: Double? = null
    ) = ResourceSource(
        type = ResourceSource.SourceType.LootTypes.ENTITY,
        filename = "entities/$entityName.json",
        producedItems = listOf(
            item(produces) to (expectedYield?.let { ResourceQuantity.ExpectedYield(it) }
                ?: ResourceQuantity.ItemQuantity(quantity))
        )
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

    /**
     * stick crafted from planks (2 -> 4); witches drop sticks; **and so do leaves**.
     *
     * The leaf source was missing until 2026-09-02, and its absence hid a real result: in
     * ingested 1.21.4 data twelve leaf blocks drop sticks with no recorded expected yield, so
     * they score a flat 100 — exactly what crafting scores once its efficiency bonus and
     * penalties net out. `stick`, the most common intermediate in the game, is a **zero-margin
     * tie** in production, decided only by the recipe-first tie-break. Without a leaf source
     * here the suite could not see that, and any future change worth one point would silently
     * turn "craft sticks" into "go break leaves".
     */
    private val stickChain = plankChain + listOf(
        recipe(
            "stick.json",
            inputs = listOf(item("minecraft:oak_planks") to 2),
            output = "minecraft:stick" to 4
        ),
        entityLoot("witch", "minecraft:stick"),
        blockLoot("oak_leaves", "minecraft:stick")
    )

    /**
     * The 16-option "smelt any iron equipment into a nugget" tag. Real ingested data
     * has this, and it is what makes the nugget route *non*-circular: without it
     * iron_nugget is only ever crafted from an ingot, the selector rejects
     * iron_ingot_from_nuggets structurally, and the smelting expectations below pass
     * for the wrong reason. See MCO-317.
     *
     * Two members stand in for the real tag's 16, and **at least one of them must have a
     * source of its own**. Until 2026-09-02 neither did: `chainmail_boots` and `iron_pickaxe`
     * appeared only in this declaration, so the tag was a dead end here while being a live
     * two-step chain in production. The comment that used to sit in this spot — "membership
     * does not affect scoring, a tag is one requirement at depth 0 regardless" — was true of
     * the fixture and false of the graph, which is the definition of a test passing for the
     * wrong reason.
     */
    private val ironEquipmentTag = MinecraftTag(
        "minecraft:smelts_to_iron_nugget", "Smelts To Iron Nugget",
        listOf(item("minecraft:chainmail_boots"), item("minecraft:iron_pickaxe"))
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
        ResourceSource(
            type = ResourceSource.SourceType.RecipeTypes.SMELTING,
            filename = "iron_nugget_from_smelting.json",
            requiredItems = listOf(ironEquipmentTag to ResourceQuantity.ItemQuantity(1)),
            producedItems = listOf(item("minecraft:iron_nugget") to ResourceQuantity.ItemQuantity(1))
        ),
        blockLoot("iron_ore", "minecraft:raw_iron"),
        // What makes the smelt-equipment route reachable at all, and therefore what makes the
        // expectations below test the scorer rather than the selector's structural rejection.
        recipe(
            "iron_pickaxe.json",
            inputs = listOf(item("minecraft:iron_ingot") to 3, item("minecraft:stick") to 2),
            output = "minecraft:iron_pickaxe" to 1
        )
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
    fun `stick - with drop-rate data, crafting wins even at small demand`() {
        // Witches average well under one stick per kill; with that yield ingested
        // the low-yield penalty makes the recipe win without threshold help.
        val sources = plankChain + listOf(
            recipe(
                "stick.json",
                inputs = listOf(item("minecraft:oak_planks") to 2),
                output = "minecraft:stick" to 4
            ),
            entityLoot("witch", "minecraft:stick", expectedYield = 0.33)
        )

        val result = plan(sources, "minecraft:stick", amount = 10)

        assertEquals("minecraft:crafting_shaped:stick.json", result.sourceKeyOf("minecraft:stick"))
    }

    @Test
    fun `leather - a one-per-kill drop keeps beating the crafting recipe`() {
        val sources = listOf(
            entityLoot("cow", "minecraft:leather", expectedYield = 1.0),
            recipe(
                "leather.json",
                inputs = listOf(item("minecraft:rabbit_hide") to 4),
                output = "minecraft:leather" to 1
            ),
            entityLoot("rabbit", "minecraft:rabbit_hide", expectedYield = 0.5)
        )

        val result = plan(sources, "minecraft:leather")

        assertEquals("minecraft:entity:entities/cow.json", result.sourceKeyOf("minecraft:leather"))
    }

    /**
     * The leaf source exists in this fixture to make *this* assertion possible, and this
     * assertion exists because the outcome it guards has **no margin at all**.
     *
     * Crafting sticks and breaking leaves score identically in ingested 1.21.4 data. The
     * planner picks crafting only because `rank()` breaks a tie recipe-first — not because the
     * scorer prefers it. Every other stick expectation in this file would keep passing if that
     * margin quietly became -1 and the answer flipped to "go break leaves"; this one would not.
     *
     * If this fails, do not adjust it. It means a weight change has moved the most common
     * intermediate in the game, and the question is whether that was intended.
     */
    @Test
    fun `stick - crafting and breaking leaves score the same, so only the tie-break decides`() {
        val graph = ItemSourceGraphBuilder.buildFromResourceSources(stickChain)
        val candidates = ScoreDiagnostics.report(graph, "minecraft:stick", demand = 10).candidates

        val crafting = candidates.first { it.sourceKey == "minecraft:crafting_shaped:stick.json" }
        val leaves = candidates.first { it.sourceKey == "minecraft:block:blocks/oak_leaves.json" }

        assertEquals(
            crafting.total, leaves.total,
            "a zero-margin tie: if these ever differ, the tie-break stopped being what decides"
        )
        assertEquals(
            "minecraft:crafting_shaped:stick.json",
            plan(stickChain, "minecraft:stick").sourceKeyOf("minecraft:stick"),
            "and recipe-first is what resolves it"
        )
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

    /**
     * The reciprocal penalty (MCO-317) demotes "9 nuggets -> 1 ingot" because for iron
     * the nugget side has no real source. Gold is the case where that route is genuinely
     * how players work: a zombified-piglin farm produces nuggets by the thousand, and you
     * craft them up. That farm is expressed as a declared supply, and the supplied bonus
     * (+30) has to outweigh the penalty (-15) or the planner would tell someone with a
     * gold farm to go mining instead.
     */
    private val goldChain = listOf(
        recipe(
            "gold_ingot_from_smelting.json",
            inputs = listOf(item("minecraft:raw_gold") to 1),
            output = "minecraft:gold_ingot" to 1,
            type = ResourceSource.SourceType.RecipeTypes.SMELTING
        ),
        recipe(
            "gold_ingot_from_nuggets.json",
            inputs = listOf(item("minecraft:gold_nugget") to 9),
            output = "minecraft:gold_ingot" to 1,
            type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS
        ),
        recipe(
            "gold_nugget_from_ingot.json",
            inputs = listOf(item("minecraft:gold_ingot") to 1),
            output = "minecraft:gold_nugget" to 9
        ),
        blockLoot("gold_ore", "minecraft:raw_gold"),
        blockLoot("nether_gold_ore", "minecraft:gold_nugget", quantity = 4),
        // Zombified piglins drop gold ingots, and leaving them out of this fixture hid what
        // production actually does with them. See the disabled expectation below.
        entityLoot("zombified_piglin", "minecraft:gold_ingot")
    )

    @Test
    fun `gold_ingot - a declared nugget farm beats mining, despite the reciprocal penalty`() {
        val result = plan(
            goldChain,
            "minecraft:gold_ingot",
            amount = 811,
            supplied = mapOf("minecraft:gold_nugget" to SupplySource.Farm("Gold farm"))
        )

        assertEquals(
            "minecraft:crafting_shapeless:gold_ingot_from_nuggets.json",
            result.sourceKeyOf("minecraft:gold_ingot")
        )
        // The farm has to be the reason, not a coincidence: the nuggets must terminate
        // as supplied rather than being gathered on. Without this the assertion above
        // would also hold if the supply were ignored entirely.
        assertEquals(PlanNodeStatus.SUPPLIED, result.nodes.getValue("minecraft:gold_nugget").status)
    }

    /**
     * **KNOWN MIS-RANK — this is what production does, and it is wrong.**
     *
     * Measured against real 1.21.4 data: `gold_ingot` resolves to
     * `entity:entities/zombified_piglin.json` at **100**, beating smelting the ore at 70,
     * because an entity drop scores a flat base and nothing prices how hard the mob is to
     * find. This fixture could not see it until the piglin drop was added, so the expectation
     * below passed for the wrong reason for as long as it has existed.
     *
     * Kept and disabled rather than rewritten to match production: smelting the ore *is* the
     * right answer for a player without a gold farm, so this records intent. It is the same
     * family as `bowl` from turtles and `cake` from a chest — an availability cost the shipped
     * scorer has no way to express, which is one of the arguments for MCO-490.
     */
    @Test
    @Disabled("KNOWN MIS-RANK: production picks the zombified-piglin drop. MCO-490.")
    fun `gold_ingot - without a nugget farm, smelting the ore wins`() {
        val result = plan(goldChain, "minecraft:gold_ingot", amount = 811)

        assertEquals(
            "minecraft:smelting:gold_ingot_from_smelting.json",
            result.sourceKeyOf("minecraft:gold_ingot")
        )
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
    fun `emerald - mining beats unpacking a chest-looted emerald block`() {
        // The real-graph case the self-block fixtures miss: the storage block is
        // *independently* obtainable (chest loot), so the unpack chain is feasible
        // and not structurally rejected. Its 9x output would earn a +160 efficiency
        // bonus and win at 240 — except a reciprocal unpack earns no efficiency, so
        // mining the ore (100) wins and the block never enters the plan.
        val sources = listOf(
            blockLoot("emerald_ore", "minecraft:emerald"),
            recipe(
                "emerald_from_emerald_block.json",
                inputs = listOf(item("minecraft:emerald_block") to 1),
                output = "minecraft:emerald" to 9,
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS
            ),
            recipe(
                "emerald_block.json",
                inputs = listOf(item("minecraft:emerald") to 9),
                output = "minecraft:emerald_block" to 1
            ),
            chestLoot("village_temple", "minecraft:emerald_block")
        )

        val result = plan(sources, "minecraft:emerald")

        assertEquals("minecraft:block:blocks/emerald_ore.json", result.sourceKeyOf("minecraft:emerald"))
        assertTrue("minecraft:emerald_block" !in result.nodes, "the storage block must not enter the plan")
    }

    @Test
    fun `iron_nugget - unpacking an ingot still wins when nuggets are otherwise only looted`() {
        // Guards the asymmetry: dropping efficiency on reciprocal unpacks must not
        // over-correct. A nugget has no "mine a nugget" path, so unpacking an ingot
        // (no efficiency, but base crafting) must still beat chest loot — otherwise
        // the plan would tell the player to loot nuggets from chests.
        val sources = listOf(
            recipe(
                "iron_ingot_from_smelting.json",
                inputs = listOf(item("minecraft:raw_iron") to 1),
                output = "minecraft:iron_ingot" to 1,
                type = ResourceSource.SourceType.RecipeTypes.SMELTING
            ),
            blockLoot("iron_ore", "minecraft:raw_iron"),
            recipe(
                "iron_nugget_from_ingot.json",
                inputs = listOf(item("minecraft:iron_ingot") to 1),
                output = "minecraft:iron_nugget" to 9
            ),
            recipe(
                "iron_ingot_from_nuggets.json",
                inputs = listOf(item("minecraft:iron_nugget") to 9),
                output = "minecraft:iron_ingot" to 1
            ),
            chestLoot("bastion_other", "minecraft:iron_nugget")
        )

        val result = plan(sources, "minecraft:iron_nugget")

        assertEquals(
            "minecraft:crafting_shaped:iron_nugget_from_ingot.json",
            result.sourceKeyOf("minecraft:iron_nugget")
        )
    }

    @Test
    fun `copper_ingot - unpacking a layered waxed block is still not efficient`() {
        // The transitive case: copper_ingot can be unpacked from waxed_copper_block,
        // which is not crafted from the ingot directly — it is copper_block + honeycomb,
        // and copper_block is the 9-ingot pack. The reciprocal check must follow that
        // chain, or the waxed unpack keeps its +160 and wins at 240. With containment
        // transitive, the unpack drops to base and the entity drop (100) wins.
        val sources = listOf(
            entityLoot("drowned", "minecraft:copper_ingot"),
            recipe(
                "copper_ingot_from_smelting.json",
                inputs = listOf(item("minecraft:raw_copper") to 1),
                output = "minecraft:copper_ingot" to 1,
                type = ResourceSource.SourceType.RecipeTypes.SMELTING
            ),
            blockLoot("copper_ore", "minecraft:raw_copper"),
            recipe(
                "copper_ingot_from_waxed_copper_block.json",
                inputs = listOf(item("minecraft:waxed_copper_block") to 1),
                output = "minecraft:copper_ingot" to 9,
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS
            ),
            recipe(
                "waxed_copper_block.json",
                inputs = listOf(item("minecraft:copper_block") to 1, item("minecraft:honeycomb") to 1),
                output = "minecraft:waxed_copper_block" to 1
            ),
            recipe(
                "copper_block.json",
                inputs = listOf(item("minecraft:copper_ingot") to 9),
                output = "minecraft:copper_block" to 1
            ),
            chestLoot("shipwreck_supply", "minecraft:waxed_copper_block"),
            blockLoot("beehive", "minecraft:honeycomb")
        )

        val result = plan(sources, "minecraft:copper_ingot")

        assertEquals("minecraft:entity:entities/drowned.json", result.sourceKeyOf("minecraft:copper_ingot"))
        assertTrue("minecraft:waxed_copper_block" !in result.nodes, "the waxed block must not enter the plan")
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

    /**
     * Reviewed 2026-09-02 and left as it is.
     *
     * The cost model prefers crafting here, at 4x cheaper, and for a moment this expectation was
     * changed to match. Even's call on seeing it: **arrows from skeletons is fine.** Arrows are
     * rarely the point of a build, so which of two defensible answers the planner gives is not
     * worth moving, and a player who owns a skeleton farm gets arrows through production data
     * either way.
     *
     * Recorded rather than silently kept, because "the two models disagree here and it does not
     * matter" is a different fact from "the two models agree", and only one of them is true.
     */
    @Test
    fun `arrow - without ingredient farms, a skeleton farm is suggested`() {
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

    // ── synthetic sources ───────────────────────────────────────────────────

    @Test
    fun `concrete prefers the powder-and-water mechanic over breaking the placed block`() {
        val sources = listOf(
            blockLoot("white_concrete", "minecraft:white_concrete"),
            recipe(
                "white_concrete.json",
                inputs = listOf(item("minecraft:white_concrete_powder") to 1),
                output = "minecraft:white_concrete" to 1,
                type = ResourceSource.SourceType.MechanicTypes.IN_WORLD_TRANSFORM
            ),
            recipe(
                "white_concrete_powder.json",
                inputs = listOf(
                    item("minecraft:sand") to 4,
                    item("minecraft:gravel") to 4,
                    item("minecraft:white_dye") to 1
                ),
                output = "minecraft:white_concrete_powder" to 8,
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS
            ),
            blockLoot("sand", "minecraft:sand"),
            blockLoot("gravel", "minecraft:gravel"),
            blockLoot("dandelion", "minecraft:white_dye")
        )

        val result = plan(sources, "minecraft:white_concrete")

        // The in-world transform counts as a constructive sibling, so breaking the placed
        // concrete is penalised as circular and the transform wins.
        assertEquals("mcorg:in_world_transform:white_concrete.json", result.sourceKeyOf("minecraft:white_concrete"))
        assertEquals(
            ActivityGroup.CRAFT,
            result.activityList.first { it.item.id == "minecraft:white_concrete" }.group
        )
    }

    @Test
    fun `honey bottle prefers bottling a beehive over chest loot`() {
        val sources = listOf(
            chestLoot("village_temple", "minecraft:honey_bottle"),
            recipe(
                "beehive_bottle.json",
                inputs = listOf(item("minecraft:glass_bottle") to 1),
                output = "minecraft:honey_bottle" to 1,
                type = ResourceSource.SourceType.LootTypes.BLOCK_INTERACT
            ),
            blockLoot("glass_bottle_src", "minecraft:glass_bottle")
        )

        val result = plan(sources, "minecraft:honey_bottle")

        assertEquals("minecraft:block_interact:beehive_bottle.json", result.sourceKeyOf("minecraft:honey_bottle"))
    }

    @Test
    fun `water is obtainable by collecting or breaking ice and groups under gather`() {
        val sources = listOf(
            recipe(
                "water.json",
                inputs = emptyList(),
                output = "minecraft:water" to 1,
                type = ResourceSource.SourceType.MechanicTypes.COLLECT
            ),
            blockLoot("ice", "minecraft:water")
        )

        val result = plan(sources, "minecraft:water")

        assertTrue(result.nodes.getValue("minecraft:water").status != PlanNodeStatus.BLOCKED, "water must be obtainable")
        assertEquals(
            ActivityGroup.GATHER,
            result.activityList.first { it.item.id == "minecraft:water" }.group
        )
    }
}
