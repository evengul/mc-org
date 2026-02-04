package app.mcorg.domain.services

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ItemSourceGraph
import app.mcorg.domain.model.resources.ResourceSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemSourceGraphQueriesTest {

    private lateinit var graph: ItemSourceGraph
    private lateinit var queries: ItemSourceGraphQueries

    @BeforeEach
    fun setup() {
        // Create a test graph with a realistic Minecraft crafting chain:
        // oak_log -> oak_planks -> [sticks, crafting_table]
        // sticks + diamonds -> diamond_pickaxe
        // diamond_ore (mining) -> diamond
        val sources = listOf(
            // Planks from logs
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS,
                filename = "oak_planks.json",
                requiredItems = listOf(Item("minecraft:oak_log", "Oak Log")),
                producedItems = listOf(Item("minecraft:oak_planks", "Oak Planks"))
            ),
            // Sticks from planks
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "stick.json",
                requiredItems = listOf(Item("minecraft:oak_planks", "Oak Planks")),
                producedItems = listOf(Item("minecraft:stick", "Stick"))
            ),
            // Crafting table from planks
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "crafting_table.json",
                requiredItems = listOf(Item("minecraft:oak_planks", "Oak Planks")),
                producedItems = listOf(Item("minecraft:crafting_table", "Crafting Table"))
            ),
            // Diamond pickaxe from diamonds and sticks
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "diamond_pickaxe.json",
                requiredItems = listOf(
                    Item("minecraft:diamond", "Diamond"),
                    Item("minecraft:stick", "Stick")
                ),
                producedItems = listOf(Item("minecraft:diamond_pickaxe", "Diamond Pickaxe"))
            ),
            // Diamond from mining (leaf item - no inputs)
            ResourceSource(
                type = ResourceSource.SourceType.LootTypes.BLOCK,
                filename = "diamond_ore.json",
                requiredItems = emptyList(),
                producedItems = listOf(Item("minecraft:diamond", "Diamond"))
            ),
            // Oak log from trees (leaf item - no inputs)
            ResourceSource(
                type = ResourceSource.SourceType.LootTypes.BLOCK,
                filename = "oak_log.json",
                requiredItems = emptyList(),
                producedItems = listOf(Item("minecraft:oak_log", "Oak Log"))
            )
        )

        graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)
        queries = ItemSourceGraphQueries(graph)
    }

    @Test
    fun `findAllSourcesForItem returns all sources`() {
        val plankSources = queries.findAllSourcesForItem("minecraft:oak_planks")
        assertEquals(1, plankSources.size)

        val stickSources = queries.findAllSourcesForItem("minecraft:stick")
        assertEquals(1, stickSources.size)
    }

    @Test
    fun `findAllSourcesForItem returns empty set for non-existent item`() {
        val sources = queries.findAllSourcesForItem("minecraft:nonexistent")
        assertTrue(sources.isEmpty())
    }

    @Test
    fun `findRequiredItemsForSource returns correct items`() {
        val stickSource = queries.findAllSourcesForItem("minecraft:stick").first()
        val requiredItems = queries.findRequiredItemsForSource(stickSource)

        assertEquals(1, requiredItems.size)
        assertEquals("minecraft:oak_planks", requiredItems.first().itemId)
    }

    @Test
    fun `findProductionChain for simple item`() {
        val tree = queries.findProductionChain("minecraft:oak_planks")

        assertNotNull(tree)
        assertEquals("minecraft:oak_planks", tree.targetItem.itemId)
        assertEquals(1, tree.sources.size)

        val branch = tree.sources.first()
        assertEquals(1, branch.requiredItems.size)
        assertEquals("minecraft:oak_log", branch.requiredItems.first().targetItem.itemId)
    }

    @Test
    fun `findProductionChain for complex item`() {
        // Diamond pickaxe requires diamonds and sticks
        // Sticks require planks, planks require logs
        val tree = queries.findProductionChain("minecraft:diamond_pickaxe")

        assertNotNull(tree)
        assertEquals("minecraft:diamond_pickaxe", tree.targetItem.itemId)
        assertEquals(1, tree.sources.size)

        val branch = tree.sources.first()
        assertEquals(2, branch.requiredItems.size) // diamond and stick

        // Verify diamond branch
        val diamondTree = branch.requiredItems.find { it.targetItem.itemId == "minecraft:diamond" }
        assertNotNull(diamondTree)
        assertEquals(1, diamondTree.sources.size) // Mining

        // Verify stick branch
        val stickTree = branch.requiredItems.find { it.targetItem.itemId == "minecraft:stick" }
        assertNotNull(stickTree)
        assertEquals(1, stickTree.sources.size)
        assertEquals(1, stickTree.sources.first().requiredItems.size) // planks
    }

    @Test
    fun `findProductionChain returns null for non-existent item`() {
        val tree = queries.findProductionChain("minecraft:nonexistent")
        assertNull(tree)
    }

    @Test
    fun `findProductionChain respects maxDepth`() {
        val tree = queries.findProductionChain("minecraft:diamond_pickaxe", maxDepth = 1)

        assertNotNull(tree)
        val branch = tree.sources.first()

        // With maxDepth=1, should have required items but no deeper recursion
        assertTrue(branch.requiredItems.isNotEmpty())

        // The required items should have empty sources (depth limit reached)
        val stickTree = branch.requiredItems.find { it.targetItem.itemId == "minecraft:stick" }
        assertNotNull(stickTree)
        assertTrue(stickTree.sources.isEmpty() || stickTree.sources.first().requiredItems.isEmpty())
    }

    @Test
    fun `findLeafItems returns base resources`() {
        val leafItems = queries.findLeafItems()

        // Oak log and diamond are leaf items (no recipe, only found)
        val leafItemIds = leafItems.map { it.itemId }.toSet()
        assertTrue(leafItemIds.contains("minecraft:oak_log"))
        assertTrue(leafItemIds.contains("minecraft:diamond"))

        // Crafted items should not be leaf items
        assertTrue(!leafItemIds.contains("minecraft:oak_planks"))
        assertTrue(!leafItemIds.contains("minecraft:stick"))
        assertTrue(!leafItemIds.contains("minecraft:diamond_pickaxe"))
    }

    @Test
    fun `findTopLevelItems returns final products`() {
        val topLevelItems = queries.findTopLevelItems()

        // Diamond pickaxe and crafting table are not used in any recipe
        val topLevelItemIds = topLevelItems.map { it.itemId }.toSet()
        assertTrue(topLevelItemIds.contains("minecraft:diamond_pickaxe"))
        assertTrue(topLevelItemIds.contains("minecraft:crafting_table"))

        // Items used in recipes should not be top-level
        assertTrue(!topLevelItemIds.contains("minecraft:stick"))
        assertTrue(!topLevelItemIds.contains("minecraft:diamond"))
        assertTrue(!topLevelItemIds.contains("minecraft:oak_planks"))
    }

    @Test
    fun `detectCycles finds no cycles in acyclic graph`() {
        val cycles = queries.detectCycles()

        // Our test graph has no cycles
        assertTrue(cycles.isEmpty())
    }

    @Test
    fun `detectCycles finds cycles in cyclic graph`() {
        // Create a graph with a cycle:
        // item_a -> item_b -> item_c -> item_a
        val cyclicSources = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "item_b.json",
                requiredItems = listOf(Item("minecraft:item_a", "Item A")),
                producedItems = listOf(Item("minecraft:item_b", "Item B"))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "item_c.json",
                requiredItems = listOf(Item("minecraft:item_b", "Item B")),
                producedItems = listOf(Item("minecraft:item_c", "Item C"))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "item_a.json",
                requiredItems = listOf(Item("minecraft:item_c", "Item C")),
                producedItems = listOf(Item("minecraft:item_a", "Item A"))
            )
        )

        val cyclicGraph = ItemSourceGraphBuilder.buildFromResourceSources(cyclicSources)
        val cyclicQueries = ItemSourceGraphQueries(cyclicGraph)

        val cycles = cyclicQueries.detectCycles()
        assertTrue(cycles.isNotEmpty())

        // Should find at least one cycle containing the three items
        val foundCycle = cycles.any { cycle ->
            cycle.containsAll(listOf("minecraft:item_a", "minecraft:item_b", "minecraft:item_c"))
        }
        assertTrue(foundCycle)
    }

    @Test
    fun `findShortestPath finds direct path`() {
        val path = queries.findShortestPath("minecraft:oak_log", "minecraft:oak_planks")

        assertNotNull(path)
        assertEquals(2, path.size)
        assertEquals("minecraft:oak_log", path[0])
        assertEquals("minecraft:oak_planks", path[1])
    }

    @Test
    fun `findShortestPath finds multi-step path`() {
        val path = queries.findShortestPath("minecraft:oak_log", "minecraft:stick")

        assertNotNull(path)
        assertEquals(3, path.size)
        assertEquals("minecraft:oak_log", path[0])
        assertEquals("minecraft:oak_planks", path[1])
        assertEquals("minecraft:stick", path[2])
    }

    @Test
    fun `findShortestPath returns null for no path`() {
        // Diamond cannot be crafted from logs
        val path = queries.findShortestPath("minecraft:oak_log", "minecraft:diamond")
        assertNull(path)
    }

    @Test
    fun `findShortestPath returns single item for same start and end`() {
        val path = queries.findShortestPath("minecraft:oak_log", "minecraft:oak_log")

        assertNotNull(path)
        assertEquals(1, path.size)
        assertEquals("minecraft:oak_log", path[0])
    }

    @Test
    fun `findShortestPath returns null for non-existent items`() {
        assertNull(queries.findShortestPath("minecraft:nonexistent", "minecraft:oak_log"))
        assertNull(queries.findShortestPath("minecraft:oak_log", "minecraft:nonexistent"))
    }

    @Test
    fun `analyzeGraph returns comprehensive statistics`() {
        val analysis = queries.analyzeGraph()

        // Should include basic graph stats
        assertTrue(analysis.containsKey("itemCount"))
        assertTrue(analysis.containsKey("sourceCount"))
        assertTrue(analysis.containsKey("totalEdges"))

        // Should include query-based stats
        assertTrue(analysis.containsKey("leafItemCount"))
        assertTrue(analysis.containsKey("topLevelItemCount"))
        assertTrue(analysis.containsKey("cycleCount"))
        assertTrue(analysis.containsKey("itemWithMostSources"))
        assertTrue(analysis.containsKey("maxSourcesPerItem"))

        // Validate some values
        assertEquals(6, analysis["itemCount"]) // log, planks, stick, table, diamond, pickaxe
        assertEquals(2, analysis["leafItemCount"]) // log, diamond
        assertEquals(2, analysis["topLevelItemCount"]) // pickaxe, table
        assertEquals(0, analysis["cycleCount"])
    }

    @Test
    fun `production chain handles items with multiple sources`() {
        // Add a second way to get planks
        val sourcesWithAlternatives = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS,
                filename = "oak_planks.json",
                requiredItems = listOf(Item("minecraft:oak_log", "Oak Log")),
                producedItems = listOf(Item("minecraft:oak_planks", "Oak Planks"))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS,
                filename = "birch_planks.json",
                requiredItems = listOf(Item("minecraft:birch_log", "Birch Log")),
                producedItems = listOf(Item("minecraft:oak_planks", "Oak Planks"))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.LootTypes.BLOCK,
                filename = "birch_log.json",
                requiredItems = emptyList(),
                producedItems = listOf(Item("minecraft:birch_log", "Birch Log"))
            )
        )

        val altGraph = ItemSourceGraphBuilder.buildFromResourceSources(sourcesWithAlternatives)
        val altQueries = ItemSourceGraphQueries(altGraph)

        val tree = altQueries.findProductionChain("minecraft:oak_planks")

        assertNotNull(tree)
        assertEquals(2, tree.sources.size) // Two ways to get planks
    }

    @Test
    fun `findShortestPath prefers shorter paths`() {
        // Create a graph with multiple paths of different lengths
        val multiPathSources = listOf(
            // Direct path: A -> B
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "direct.json",
                requiredItems = listOf(Item("minecraft:item_a", "Item A")),
                producedItems = listOf(Item("minecraft:item_b", "Item B"))
            ),
            // Longer path: A -> X -> Y -> B
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "step1.json",
                requiredItems = listOf(Item("minecraft:item_a", "Item A")),
                producedItems = listOf(Item("minecraft:item_x", "Item X"))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "step2.json",
                requiredItems = listOf(Item("minecraft:item_x", "Item X")),
                producedItems = listOf(Item("minecraft:item_y", "Item Y"))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "step3.json",
                requiredItems = listOf(Item("minecraft:item_y", "Item Y")),
                producedItems = listOf(Item("minecraft:item_b", "Item B"))
            )
        )

        val multiPathGraph = ItemSourceGraphBuilder.buildFromResourceSources(multiPathSources)
        val multiPathQueries = ItemSourceGraphQueries(multiPathGraph)

        val path = multiPathQueries.findShortestPath("minecraft:item_a", "minecraft:item_b")

        assertNotNull(path)
        // Should find the direct path (length 2) not the longer path (length 4)
        assertEquals(2, path.size)
        assertEquals("minecraft:item_a", path[0])
        assertEquals("minecraft:item_b", path[1])
    }
}

