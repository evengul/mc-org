package app.mcorg.domain.model.resources

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemSourceGraphTest {

    @Test
    fun `empty graph has no nodes or edges`() {
        val graph = ItemSourceGraph.builder().build()

        assertEquals(0, graph.getAllItems().size)
        assertEquals(0, graph.getAllSources().size)

        val stats = graph.getStatistics()
        assertEquals(0, stats["itemCount"])
        assertEquals(0, stats["sourceCount"])
        assertEquals(0, stats["totalEdges"])
    }

    @Test
    fun `can add item nodes`() {
        val builder = ItemSourceGraph.builder()

        val stick = builder.addItemNode("minecraft:stick")
        val planks = builder.addItemNode("minecraft:planks")

        val graph = builder.build()

        assertEquals(2, graph.getAllItems().size)
        assertNotNull(graph.getItemNode("minecraft:stick"))
        assertNotNull(graph.getItemNode("minecraft:planks"))
    }

    @Test
    fun `can add source nodes`() {
        val builder = ItemSourceGraph.builder()

        val craftingSource = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "stick.json")
        val miningSource = builder.addSourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "diamond_ore.json")

        val graph = builder.build()

        assertEquals(2, graph.getAllSources().size)
        assertNotNull(graph.getSourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED.id, "stick.json"))
        assertNotNull(graph.getSourceNode(ResourceSource.SourceType.LootTypes.BLOCK.id, "diamond_ore.json"))
    }

    @Test
    fun `duplicate item nodes are deduplicated`() {
        val builder = ItemSourceGraph.builder()

        val stick1 = builder.addItemNode("minecraft:stick")
        val stick2 = builder.addItemNode("minecraft:stick")

        val graph = builder.build()

        assertEquals(1, graph.getAllItems().size)
        assertEquals(stick1, stick2)
    }

    @Test
    fun `duplicate source nodes are deduplicated`() {
        val builder = ItemSourceGraph.builder()

        val source1 = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "stick.json")
        val source2 = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "stick.json")

        val graph = builder.build()

        assertEquals(1, graph.getAllSources().size)
        assertEquals(source1, source2)
    }

    @Test
    fun `simple linear chain - planks to sticks`() {
        // Arrange: planks -> [crafting] -> sticks
        val builder = ItemSourceGraph.builder()

        val planks = builder.addItemNode("minecraft:planks")
        val sticks = builder.addItemNode("minecraft:stick")
        val craftingSource = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "stick.json")

        builder.addItemToSourceEdge(planks, craftingSource)
        builder.addSourceToItemEdge(craftingSource, sticks)

        val graph = builder.build()

        // Assert
        val sticksProducedBy = graph.getSourcesForItem("minecraft:stick")
        assertEquals(1, sticksProducedBy.size)
        assertTrue(sticksProducedBy.contains(craftingSource))

        val requiredItems = graph.getRequiredItems(craftingSource)
        assertEquals(1, requiredItems.size)
        assertTrue(requiredItems.contains(planks))

        val producedItems = graph.getProducedItems(craftingSource)
        assertEquals(1, producedItems.size)
        assertTrue(producedItems.contains(sticks))
    }

    @Test
    fun `multiple sources for same item - iron from mining and smelting`() {
        // Arrange:
        // iron_ore -> [mining] -> iron_ingot
        // raw_iron -> [smelting] -> iron_ingot
        val builder = ItemSourceGraph.builder()

        val ironOre = builder.addItemNode("minecraft:iron_ore")
        val rawIron = builder.addItemNode("minecraft:raw_iron")
        val ironIngot = builder.addItemNode("minecraft:iron_ingot")

        val miningSource = builder.addSourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "iron_ore.json")
        val smeltingSource = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.SMELTING, "iron_ingot.json")

        // Mining produces iron from ore
        builder.addItemToSourceEdge(ironOre, miningSource)
        builder.addSourceToItemEdge(miningSource, ironIngot)

        // Smelting produces iron from raw iron
        builder.addItemToSourceEdge(rawIron, smeltingSource)
        builder.addSourceToItemEdge(smeltingSource, ironIngot)

        val graph = builder.build()

        // Assert
        val ironSources = graph.getSourcesForItem("minecraft:iron_ingot")
        assertEquals(2, ironSources.size)
        assertTrue(ironSources.contains(miningSource))
        assertTrue(ironSources.contains(smeltingSource))

        assertEquals(setOf(ironOre), graph.getRequiredItems(miningSource))
        assertEquals(setOf(rawIron), graph.getRequiredItems(smeltingSource))
    }

    @Test
    fun `loot source has no required items`() {
        // Arrange: [chest] -> diamond
        val builder = ItemSourceGraph.builder()

        val diamond = builder.addItemNode("minecraft:diamond")
        val chestSource = builder.addSourceNode(ResourceSource.SourceType.LootTypes.CHEST, "stronghold.json")

        builder.addSourceToItemEdge(chestSource, diamond)

        val graph = builder.build()

        // Assert
        val requiredItems = graph.getRequiredItems(chestSource)
        assertTrue(requiredItems.isEmpty())

        val diamondSources = graph.getSourcesForItem("minecraft:diamond")
        assertEquals(1, diamondSources.size)
    }

    @Test
    fun `complex recipe with multiple inputs and outputs`() {
        // Arrange: [diamond + stick + stick] -> [crafting] -> diamond_pickaxe
        val builder = ItemSourceGraph.builder()

        val diamond = builder.addItemNode("minecraft:diamond")
        val stick = builder.addItemNode("minecraft:stick")
        val diamondPickaxe = builder.addItemNode("minecraft:diamond_pickaxe")

        val craftingSource = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "diamond_pickaxe.json")

        builder.addItemToSourceEdge(diamond, craftingSource)
        builder.addItemToSourceEdge(stick, craftingSource)
        builder.addSourceToItemEdge(craftingSource, diamondPickaxe)

        val graph = builder.build()

        // Assert
        val requiredItems = graph.getRequiredItems(craftingSource)
        assertEquals(2, requiredItems.size) // Deduplicated: diamond and stick
        assertTrue(requiredItems.contains(diamond))
        assertTrue(requiredItems.contains(stick))

        val producedItems = graph.getProducedItems(craftingSource)
        assertEquals(1, producedItems.size)
        assertTrue(producedItems.contains(diamondPickaxe))
    }

    @Test
    fun `getItemNode returns null for non-existent item`() {
        val graph = ItemSourceGraph.builder().build()

        assertNull(graph.getItemNode("minecraft:nonexistent"))
    }

    @Test
    fun `getSourceNode returns null for non-existent source`() {
        val graph = ItemSourceGraph.builder().build()

        assertNull(graph.getSourceNode("minecraft:nonexistent", "test.json"))
    }

    @Test
    fun `getSourcesForItem returns empty set for non-existent item`() {
        val graph = ItemSourceGraph.builder().build()

        val sources = graph.getSourcesForItem("minecraft:nonexistent")
        assertTrue(sources.isEmpty())
    }

    @Test
    fun `getSourcesForItem returns empty set for item with no sources`() {
        val builder = ItemSourceGraph.builder()
        builder.addItemNode("minecraft:diamond")
        val graph = builder.build()

        val sources = graph.getSourcesForItem("minecraft:diamond")
        assertTrue(sources.isEmpty())
    }

    @Test
    fun `graph statistics are calculated correctly`() {
        // Arrange: Create a small graph
        // planks -> [crafting] -> stick
        // diamond -> [mining] -> diamond_ore
        val builder = ItemSourceGraph.builder()

        val planks = builder.addItemNode("minecraft:planks")
        val stick = builder.addItemNode("minecraft:stick")
        val diamond = builder.addItemNode("minecraft:diamond")
        val diamondOre = builder.addItemNode("minecraft:diamond_ore")

        val craftingSource = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "stick.json")
        val miningSource = builder.addSourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "diamond_ore.json")

        builder.addItemToSourceEdge(planks, craftingSource)
        builder.addSourceToItemEdge(craftingSource, stick)

        builder.addItemToSourceEdge(diamondOre, miningSource)
        builder.addSourceToItemEdge(miningSource, diamond)

        val graph = builder.build()

        // Assert
        val stats = graph.getStatistics()
        assertEquals(4, stats["itemCount"])
        assertEquals(2, stats["sourceCount"])
        assertEquals(2, stats["itemToSourceEdges"])
        assertEquals(2, stats["sourceToItemEdges"])
        assertEquals(4, stats["totalEdges"])
    }

    @Test
    fun `cycle detection scenario - crafting table needs planks`() {
        // This tests that cycles can be represented (not infinite loops)
        // logs -> [crafting] -> planks
        // planks -> [crafting_table_recipe] -> crafting_table
        // (but you need crafting_table to craft planks efficiently)

        val builder = ItemSourceGraph.builder()

        val logs = builder.addItemNode("minecraft:oak_log")
        val planks = builder.addItemNode("minecraft:oak_planks")
        val craftingTable = builder.addItemNode("minecraft:crafting_table")

        val planksRecipe = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS, "oak_planks.json")
        val tableRecipe = builder.addSourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "crafting_table.json")

        // logs -> planks
        builder.addItemToSourceEdge(logs, planksRecipe)
        builder.addSourceToItemEdge(planksRecipe, planks)

        // planks -> crafting_table
        builder.addItemToSourceEdge(planks, tableRecipe)
        builder.addSourceToItemEdge(tableRecipe, craftingTable)

        val graph = builder.build()

        // Assert: Graph can represent this without issues
        assertEquals(3, graph.getAllItems().size)
        assertEquals(2, graph.getAllSources().size)

        // Verify relationships
        val planksRequired = graph.getRequiredItems(tableRecipe)
        assertTrue(planksRequired.contains(planks))

        val planksSources = graph.getSourcesForItem("minecraft:oak_planks")
        assertEquals(1, planksSources.size)
    }

    @Test
    fun `ItemNode toString is formatted correctly`() {
        val node = ItemNode("minecraft:diamond")
        assertEquals("Item(minecraft:diamond)", node.toString())
    }

    @Test
    fun `SourceNode toString is formatted correctly`() {
        val node = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "stick.json")
        assertEquals("Source(minecraft:crafting_shaped:stick.json)", node.toString())
    }

    @Test
    fun `GraphEdge ItemToSource toString is formatted correctly`() {
        val item = ItemNode("minecraft:stick")
        val source = SourceNode(ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED, "stick.json")
        val edge = GraphEdge.ItemToSource(item, source)

        assertTrue(edge.toString().contains("minecraft:stick"))
        assertTrue(edge.toString().contains("minecraft:crafting_shaped"))
    }

    @Test
    fun `GraphEdge SourceToItem toString is formatted correctly`() {
        val item = ItemNode("minecraft:diamond")
        val source = SourceNode(ResourceSource.SourceType.LootTypes.BLOCK, "diamond_ore.json")
        val edge = GraphEdge.SourceToItem(source, item)

        assertTrue(edge.toString().contains("minecraft:diamond"))
        assertTrue(edge.toString().contains("minecraft:block"))
    }
}

