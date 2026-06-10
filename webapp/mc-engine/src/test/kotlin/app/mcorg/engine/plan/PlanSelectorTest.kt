package app.mcorg.engine.plan

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.ItemSourceGraph
import app.mcorg.engine.model.SourceNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanSelectorTest {

    private val crafting = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED
    private val block = ResourceSource.SourceType.LootTypes.BLOCK
    private val entity = ResourceSource.SourceType.LootTypes.ENTITY
    private val chestLoot = ResourceSource.SourceType.LootTypes.CHEST

    private fun item(name: String) = Item("minecraft:$name", name)

    private val log = item("oak_log")
    private val planks = item("oak_planks")
    private val chest = item("chest")
    private val stick = item("stick")

    private class GraphFixture {
        private val builder = ItemSourceGraph.builder()

        fun recipe(filename: String, output: Pair<MinecraftId, Int>, vararg inputs: Pair<MinecraftId, Int>) {
            source(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, filename, output, *inputs)
        }

        fun source(
            type: ResourceSource.SourceType,
            filename: String,
            output: Pair<MinecraftId, Int>,
            vararg inputs: Pair<MinecraftId, Int>
        ) {
            val sourceNode = builder.addSourceNode(type, filename)
            builder.addSourceToItemEdge(sourceNode, builder.addItemNode(output.first), output.second)
            for ((inputItem, quantity) in inputs) {
                builder.addItemToSourceEdge(builder.addItemNode(inputItem), sourceNode, quantity)
            }
        }

        fun item(minecraftId: MinecraftId) {
            builder.addItemNode(minecraftId)
        }

        fun build(): ItemSourceGraph = builder.build()
    }

    /** log (mine) -> planks (craft x4, or chest loot) -> chest (craft, 8 planks) / stick (craft x4, 2 planks) */
    private fun woodGraph(): ItemSourceGraph = GraphFixture().apply {
        source(block, "blocks/oak_log.json", log to 1)
        recipe("oak_planks.json", planks to 4, log to 1)
        source(chestLoot, "chests/bonus_chest.json", planks to 1)
        recipe("chest.json", chest to 1, planks to 8)
        recipe("stick.json", stick to 4, planks to 2)
    }.build()

    private fun sourceKey(type: ResourceSource.SourceType, filename: String) = "${type.id}:$filename"

    // ── default selection ───────────────────────────────────────────────────

    @Test
    fun `default selection picks the crafting chain over chest loot`() {
        val dag = PlanSelector.select(woodGraph(), listOf(PlanTarget(chest, 1)))

        val chestNode = dag.nodes.getValue("minecraft:chest")
        assertEquals(PlanNodeStatus.RESOLVED, chestNode.status)
        assertEquals(sourceKey(crafting, "chest.json"), chestNode.source?.getKey())

        val planksNode = dag.nodes.getValue("minecraft:oak_planks")
        assertEquals(PlanNodeStatus.RESOLVED, planksNode.status)
        assertEquals(sourceKey(crafting, "oak_planks.json"), planksNode.source?.getKey())
        assertEquals(4, planksNode.producedQuantity)

        val logNode = dag.nodes.getValue("minecraft:oak_log")
        assertEquals(PlanNodeStatus.RAW_GATHER, logNode.status)
        assertEquals(sourceKey(block, "blocks/oak_log.json"), logNode.source?.getKey())
    }

    @Test
    fun `shared ingredient resolves to a single node`() {
        val dag = PlanSelector.select(woodGraph(), listOf(PlanTarget(chest, 4), PlanTarget(stick, 16)))

        assertEquals(1, dag.nodes.keys.count { it == "minecraft:oak_planks" })
        val chestRequires = dag.nodes.getValue("minecraft:chest").requires.single()
        val stickRequires = dag.nodes.getValue("minecraft:stick").requires.single()
        assertEquals("minecraft:oak_planks", chestRequires.itemId)
        assertEquals("minecraft:oak_planks", stickRequires.itemId)
        assertEquals(8, chestRequires.quantityPerCraft)
        assertEquals(2, stickRequires.quantityPerCraft)
    }

    @Test
    fun `targets with non-positive amounts are skipped`() {
        val dag = PlanSelector.select(woodGraph(), listOf(PlanTarget(chest, 0)))

        assertTrue(dag.nodes.isEmpty())
        assertTrue(dag.roots.isEmpty())
    }

    // ── supplied terminals ──────────────────────────────────────────────────

    @Test
    fun `supplied intermediate terminates expansion and carries its label`() {
        val farm = SupplySource.Farm("Bamboo plank farm")
        val dag = PlanSelector.select(
            woodGraph(),
            listOf(PlanTarget(chest, 1)),
            supplied = mapOf("minecraft:oak_planks" to farm)
        )

        val planksNode = dag.nodes.getValue("minecraft:oak_planks")
        assertEquals(PlanNodeStatus.SUPPLIED, planksNode.status)
        assertEquals(farm, planksNode.supply)
        assertTrue(planksNode.requires.isEmpty())
        assertNull(dag.nodes["minecraft:oak_log"])
    }

    @Test
    fun `supplied target is a single supplied node`() {
        val linked = SupplySource.LinkedProject(42, "Chest factory")
        val dag = PlanSelector.select(
            woodGraph(),
            listOf(PlanTarget(chest, 64)),
            supplied = mapOf("minecraft:chest" to linked)
        )

        assertEquals(setOf("minecraft:chest"), dag.nodes.keys)
        assertEquals(PlanNodeStatus.SUPPLIED, dag.nodes.getValue("minecraft:chest").status)
        assertEquals(linked, dag.nodes.getValue("minecraft:chest").supply)
    }

    // ── overrides ───────────────────────────────────────────────────────────

    @Test
    fun `a pinned source wins regardless of score`() {
        val dag = PlanSelector.select(
            woodGraph(),
            listOf(PlanTarget(chest, 1)),
            overrides = PlanOverrides(
                sourceByItem = mapOf("minecraft:oak_planks" to sourceKey(chestLoot, "chests/bonus_chest.json"))
            )
        )

        val planksNode = dag.nodes.getValue("minecraft:oak_planks")
        assertEquals(PlanNodeStatus.RAW_GATHER, planksNode.status)
        assertEquals(sourceKey(chestLoot, "chests/bonus_chest.json"), planksNode.source?.getKey())
        assertNull(dag.nodes["minecraft:oak_log"])
    }

    @Test
    fun `a pin on an unknown source leaves the node blocked`() {
        val dag = PlanSelector.select(
            woodGraph(),
            listOf(PlanTarget(chest, 1)),
            overrides = PlanOverrides(
                sourceByItem = mapOf("minecraft:oak_planks" to sourceKey(crafting, "no_such_recipe.json"))
            )
        )

        assertEquals(PlanNodeStatus.BLOCKED, dag.nodes.getValue("minecraft:oak_planks").status)
        assertFalse(dag.nodes.containsKey("minecraft:oak_log"))
    }

    // ── tags ────────────────────────────────────────────────────────────────

    private val oakPlanks = item("oak_planks")
    private val birchPlanks = item("birch_planks")
    private val birchLog = item("birch_log")
    private val planksTag = MinecraftTag("minecraft:planks", "Planks", listOf(Item("minecraft:oak_planks", "oak_planks"), Item("minecraft:birch_planks", "birch_planks")))

    private fun tagGraph(): ItemSourceGraph = GraphFixture().apply {
        item(planksTag)
        source(block, "blocks/oak_log.json", log to 1)
        source(block, "blocks/birch_log.json", birchLog to 1)
        recipe("oak_planks.json", oakPlanks to 4, log to 1)
        recipe("birch_planks.json", birchPlanks to 4, birchLog to 1)
        recipe("chest.json", chest to 1, planksTag to 8)
    }.build()

    @Test
    fun `a tag ingredient stays open for the user to disambiguate`() {
        val dag = PlanSelector.select(tagGraph(), listOf(PlanTarget(chest, 1)))

        val tagNode = dag.nodes.getValue("minecraft:planks")
        assertEquals(PlanNodeStatus.OPEN_TAG, tagNode.status)
        assertTrue(tagNode.requires.isEmpty())
        assertNull(tagNode.source)
        assertNull(dag.nodes["minecraft:oak_planks"])
    }

    @Test
    fun `a tag-member override substitutes the member and expands its chain`() {
        val dag = PlanSelector.select(
            tagGraph(),
            listOf(PlanTarget(chest, 1)),
            overrides = PlanOverrides(tagMember = mapOf("minecraft:planks" to "minecraft:birch_planks"))
        )

        assertNull(dag.nodes["minecraft:planks"])
        val chestRequires = dag.nodes.getValue("minecraft:chest").requires.single()
        assertEquals("minecraft:birch_planks", chestRequires.itemId)
        assertEquals(PlanNodeStatus.RESOLVED, dag.nodes.getValue("minecraft:birch_planks").status)
        assertEquals(PlanNodeStatus.RAW_GATHER, dag.nodes.getValue("minecraft:birch_log").status)
    }

    @Test
    fun `a tag-member override on a tag target redirects the root`() {
        val dag = PlanSelector.select(
            tagGraph(),
            listOf(PlanTarget(planksTag, 12)),
            overrides = PlanOverrides(tagMember = mapOf("minecraft:planks" to "minecraft:oak_planks"))
        )

        assertEquals("minecraft:oak_planks", dag.roots["minecraft:planks"])
        assertEquals(PlanNodeStatus.RESOLVED, dag.nodes.getValue("minecraft:oak_planks").status)
    }

    @Test
    fun `an override naming a non-member is ignored`() {
        val dag = PlanSelector.select(
            tagGraph(),
            listOf(PlanTarget(chest, 1)),
            overrides = PlanOverrides(tagMember = mapOf("minecraft:planks" to "minecraft:diamond"))
        )

        assertEquals(PlanNodeStatus.OPEN_TAG, dag.nodes.getValue("minecraft:planks").status)
    }

    // ── cycles and dead ends ────────────────────────────────────────────────

    private val diamond = item("diamond")
    private val diamondBlock = item("diamond_block")

    /** diamond: mine ore, or unpack a block that is itself crafted from 9 diamonds. */
    private fun storageBlockGraph(): ItemSourceGraph = GraphFixture().apply {
        source(block, "blocks/diamond_ore.json", diamond to 1)
        recipe("diamond.json", diamond to 9, diamondBlock to 1)
        recipe("diamond_block.json", diamondBlock to 1, diamond to 9)
    }.build()

    @Test
    fun `unpacking a storage block crafted from the item itself is never selected`() {
        val dag = PlanSelector.select(storageBlockGraph(), listOf(PlanTarget(diamond, 10)))

        val diamondNode = dag.nodes.getValue("minecraft:diamond")
        assertEquals(PlanNodeStatus.RAW_GATHER, diamondNode.status)
        assertEquals(sourceKey(block, "blocks/diamond_ore.json"), diamondNode.source?.getKey())
        assertNull(dag.nodes["minecraft:diamond_block"])
    }

    @Test
    fun `a pure crafting cycle surfaces as a blocked node, not an endless plan`() {
        val a = item("a")
        val b = item("b")
        val graph = GraphFixture().apply {
            recipe("a.json", a to 1, b to 1)
            recipe("b.json", b to 1, a to 1)
        }.build()

        val dag = PlanSelector.select(graph, listOf(PlanTarget(a, 1)))

        // The chain expands as far as it can and shows the failure point.
        assertEquals(PlanNodeStatus.RESOLVED, dag.nodes.getValue("minecraft:a").status)
        assertEquals(PlanNodeStatus.BLOCKED, dag.nodes.getValue("minecraft:b").status)
    }

    @Test
    fun `an item with no sources at all is blocked`() {
        val bedrock = item("bedrock")
        val graph = GraphFixture().apply { item(bedrock) }.build()

        val dag = PlanSelector.select(graph, listOf(PlanTarget(bedrock, 1)))

        assertEquals(PlanNodeStatus.BLOCKED, dag.nodes.getValue("minecraft:bedrock").status)
    }

    @Test
    fun `an item missing from the graph is blocked but still quantified later`() {
        val dag = PlanSelector.select(woodGraph(), listOf(PlanTarget(item("unobtainium"), 3)))

        assertEquals(PlanNodeStatus.BLOCKED, dag.nodes.getValue("minecraft:unobtainium").status)
    }

    @Test
    fun `a candidate requiring a dead-end ingredient loses to a viable alternative`() {
        val glass = item("glass")
        val sand = item("sand")
        val voidShard = item("void_shard") // exists as an ingredient, no sources
        val graph = GraphFixture().apply {
            source(block, "blocks/sand.json", sand to 1)
            // The "better" recipe needs an unobtainable catalyst.
            recipe("glass_fast.json", glass to 8, voidShard to 1)
            source(ResourceSource.SourceType.RecipeTypes.SMELTING, "glass.json", glass to 1, sand to 1)
        }.build()

        val dag = PlanSelector.select(graph, listOf(PlanTarget(glass, 4)))

        val glassNode = dag.nodes.getValue("minecraft:glass")
        assertEquals(sourceKey(ResourceSource.SourceType.RecipeTypes.SMELTING, "glass.json"), glassNode.source?.getKey())
        assertNull(dag.nodes["minecraft:void_shard"])
    }

    @Test
    fun `depth limit cuts runaway chains with a blocked node`() {
        val items = (0..5).map { item("chain_$it") }
        val graph = GraphFixture().apply {
            for (i in 0 until 5) {
                recipe("chain_$i.json", items[i] to 1, items[i + 1] to 1)
            }
            source(block, "blocks/chain_5.json", items[5] to 1)
        }.build()

        val dag = PlanSelector.select(
            graph,
            listOf(PlanTarget(items[0], 1)),
            context = PlanContext(maxDepth = 3)
        )

        assertEquals(PlanNodeStatus.RESOLVED, dag.nodes.getValue("minecraft:chain_2").status)
        assertEquals(PlanNodeStatus.BLOCKED, dag.nodes.getValue("minecraft:chain_3").status)
        assertNull(dag.nodes["minecraft:chain_4"])
    }

    // ── scoring-sensitive selection ─────────────────────────────────────────

    @Test
    fun `breaking the block that is the item itself loses to crafting it`() {
        val beacon = item("beacon")
        val ingredient = item("nether_star")
        val graph = GraphFixture().apply {
            source(block, "blocks/beacon.json", beacon to 1)
            recipe("beacon.json", beacon to 1, ingredient to 1)
            source(entity, "entities/wither.json", ingredient to 1)
        }.build()

        val dag = PlanSelector.select(graph, listOf(PlanTarget(beacon, 1)))

        assertEquals(sourceKey(crafting, "beacon.json"), dag.nodes.getValue("minecraft:beacon").source?.getKey())
    }

    @Test
    fun `bulk demand flips loot to recipe at the threshold`() {
        val witchDrop = sourceKey(entity, "entities/witch.json")
        val craftKey = sourceKey(crafting, "stick.json")
        val graph = GraphFixture().apply {
            source(block, "blocks/oak_log.json", log to 1)
            recipe("oak_planks.json", planks to 4, log to 1)
            recipe("stick.json", stick to 4, planks to 2)
            source(entity, "entities/witch.json", stick to 1)
        }.build()

        // Small demand: entity loot currently outranks the recipe (known limitation
        // until drop-rate data exists — see MCO-196).
        val small = PlanSelector.select(graph, listOf(PlanTarget(stick, 10)))
        assertEquals(witchDrop, small.nodes.getValue("minecraft:stick").source?.getKey())

        // Bulk demand: the recipe-threshold bonus flips it to crafting.
        val bulk = PlanSelector.select(graph, listOf(PlanTarget(stick, 256)))
        assertEquals(craftKey, bulk.nodes.getValue("minecraft:stick").source?.getKey())
    }

    @Test
    fun `supplied ingredients pull selection toward the recipe that uses them`() {
        val arrow = item("arrow")
        val flint = item("flint")
        val feather = item("feather")
        val graph = GraphFixture().apply {
            source(entity, "entities/skeleton.json", arrow to 1)
            recipe("arrow.json", arrow to 4, flint to 1, stick to 1, feather to 1)
            source(block, "blocks/gravel.json", flint to 1)
            source(entity, "entities/chicken.json", feather to 1)
            source(block, "blocks/oak_log.json", log to 1)
            recipe("oak_planks.json", planks to 4, log to 1)
            recipe("stick.json", stick to 4, planks to 2)
        }.build()

        val withoutFarms = PlanSelector.select(graph, listOf(PlanTarget(arrow, 16)))
        assertEquals(
            sourceKey(entity, "entities/skeleton.json"),
            withoutFarms.nodes.getValue("minecraft:arrow").source?.getKey()
        )

        val farms = mapOf(
            "minecraft:flint" to SupplySource.Farm("Gravel dupe"),
            "minecraft:stick" to SupplySource.Farm("Stick farm"),
            "minecraft:feather" to SupplySource.Farm("Chicken farm")
        )
        val withFarms = PlanSelector.select(graph, listOf(PlanTarget(arrow, 16)), supplied = farms)
        assertEquals(
            sourceKey(crafting, "arrow.json"),
            withFarms.nodes.getValue("minecraft:arrow").source?.getKey()
        )
        assertEquals(PlanNodeStatus.SUPPLIED, withFarms.nodes.getValue("minecraft:stick").status)
    }

    // ── determinism ─────────────────────────────────────────────────────────

    @Test
    fun `equal-scored candidates break ties on recipe first, then source key`() {
        val gravel = item("gravel")
        val graph = GraphFixture().apply {
            source(chestLoot, "chests/zebra.json", gravel to 1)
            source(chestLoot, "chests/alpha.json", gravel to 1)
        }.build()

        val dag = PlanSelector.select(graph, listOf(PlanTarget(gravel, 1)))

        assertEquals(sourceKey(chestLoot, "chests/alpha.json"), dag.nodes.getValue("minecraft:gravel").source?.getKey())
    }

    @Test
    fun `selection is deterministic across runs`() {
        fun run(): List<String> = PlanSelector.select(
            woodGraph(),
            listOf(PlanTarget(chest, 4), PlanTarget(stick, 64))
        ).nodes.map { (id, node) -> "$id=${node.source?.getKey()}:${node.status}" }

        val first = run()
        repeat(5) { assertEquals(first, run()) }
    }

    // ── roots ───────────────────────────────────────────────────────────────

    @Test
    fun `roots map every target to its node`() {
        val dag = PlanSelector.select(woodGraph(), listOf(PlanTarget(chest, 4), PlanTarget(stick, 64)))

        assertEquals("minecraft:chest", dag.roots["minecraft:chest"])
        assertEquals("minecraft:stick", dag.roots["minecraft:stick"])
        assertNotNull(dag.nodes[dag.roots["minecraft:chest"]])
    }
}
