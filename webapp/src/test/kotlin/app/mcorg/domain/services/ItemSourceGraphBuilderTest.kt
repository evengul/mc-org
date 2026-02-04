package app.mcorg.domain.services

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.minecraft.extract.ExtractResourceSources
import app.mcorg.test.utils.TestUtils
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ItemSourceGraphBuilderTest {

    @Test
    fun `builds graph from empty source list`() {
        val graph = ItemSourceGraphBuilder.buildFromResourceSources(emptyList())

        assertEquals(0, graph.getAllItems().size)
        assertEquals(0, graph.getAllSources().size)

        val stats = graph.getStatistics()
        assertEquals(0, stats["itemCount"])
        assertEquals(0, stats["sourceCount"])
        assertEquals(0, stats["totalEdges"])
    }

    @Test
    fun `builds graph from single recipe source`() {
        // Simple crafting recipe: 2 planks -> 4 sticks
        val sources = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "stick.json",
                requiredItems = listOf(Item("minecraft:oak_planks", "Oak Planks")),
                producedItems = listOf(Item("minecraft:stick", "Stick"))
            )
        )

        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)

        // Verify graph structure
        assertEquals(2, graph.getAllItems().size) // planks and sticks
        assertEquals(1, graph.getAllSources().size)

        // Verify relationships
        val stickSources = graph.getSourcesForItem("minecraft:stick")
        assertEquals(1, stickSources.size)

        val source = stickSources.first()
        val requiredItems = graph.getRequiredItems(source)
        assertEquals(1, requiredItems.size)
        assertEquals("minecraft:oak_planks", requiredItems.first().itemId)
    }

    @Test
    fun `builds graph from multiple recipes sharing items`() {
        // Recipe 1: logs -> planks
        // Recipe 2: planks -> sticks
        // Recipe 3: planks -> crafting_table
        val sources = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS,
                filename = "oak_planks.json",
                requiredItems = listOf(Item("minecraft:oak_log", "Oak Log")),
                producedItems = listOf(Item("minecraft:oak_planks", "Oak Planks"))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "stick.json",
                requiredItems = listOf(Item("minecraft:oak_planks", "Oak Planks")),
                producedItems = listOf(Item("minecraft:stick", "Stick"))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "crafting_table.json",
                requiredItems = listOf(Item("minecraft:oak_planks", "Oak Planks")),
                producedItems = listOf(Item("minecraft:crafting_table", "Crafting Table"))
            )
        )

        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)

        // Verify nodes
        assertEquals(4, graph.getAllItems().size) // log, planks, stick, crafting_table
        // Now each ResourceSource is unique: 3 source nodes (even though 2 share same type)
        assertEquals(3, graph.getAllSources().size)

        // Verify planks is used in multiple recipes
        assertNotNull(graph.getItemNode("minecraft:oak_planks"))
        val planksNode = graph.getItemNode("minecraft:oak_planks")!!

        // Planks should be produced by one source (CRAFTING_SHAPELESS from oak_planks.json)
        val planksProducers = graph.getSourcesForItem("minecraft:oak_planks")
        assertEquals(1, planksProducers.size)

        // stick and crafting_table have separate sources now (different filenames)
        val stickSources = graph.getSourcesForItem("minecraft:stick")
        val tableSources = graph.getSourcesForItem("minecraft:crafting_table")

        assertEquals(1, stickSources.size)
        assertEquals(1, tableSources.size)
        // They should be different sources (different filenames)
        assertTrue(stickSources.first() != tableSources.first())

        // Each source should require planks
        assertTrue(graph.getRequiredItems(stickSources.first()).contains(planksNode))
        assertTrue(graph.getRequiredItems(tableSources.first()).contains(planksNode))
    }

    @Test
    fun `builds graph with loot sources (no inputs)`() {
        val sources = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.LootTypes.BLOCK,
                filename = "diamond_ore.json",
                requiredItems = emptyList(),
                producedItems = listOf(Item("minecraft:diamond", "Diamond"))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.LootTypes.CHEST,
                filename = "stronghold_corridor.json",
                requiredItems = emptyList(),
                producedItems = listOf(
                    Item("minecraft:diamond", "Diamond"),
                    Item("minecraft:emerald", "Emerald")
                )
            )
        )

        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)

        // Verify structure
        assertEquals(2, graph.getAllItems().size) // diamond, emerald
        assertEquals(2, graph.getAllSources().size)

        // Diamond has two sources
        val diamondSources = graph.getSourcesForItem("minecraft:diamond")
        assertEquals(2, diamondSources.size)

        // Both sources should have no requirements
        diamondSources.forEach { source ->
            assertTrue(graph.getRequiredItems(source).isEmpty())
        }
    }

    @Test
    fun `handles duplicate source definitions with same type`() {
        // Two different recipes with same source type but different filenames
        val sources = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "stick.json",
                requiredItems = listOf(Item("minecraft:oak_planks", "Oak Planks")),
                producedItems = listOf(Item("minecraft:stick", "Stick"))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "ladder.json",
                requiredItems = listOf(Item("minecraft:stick", "Stick")),
                producedItems = listOf(Item("minecraft:ladder", "Ladder"))
            )
        )

        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)

        // Should have 3 items (planks, stick, ladder)
        assertEquals(3, graph.getAllItems().size)

        // Now each ResourceSource is unique: 2 source nodes (different filenames)
        assertEquals(2, graph.getAllSources().size)

        // Each source produces one item
        val stickSource = graph.getSourcesForItem("minecraft:stick").first()
        val ladderSource = graph.getSourcesForItem("minecraft:ladder").first()

        assertEquals(1, graph.getProducedItems(stickSource).size)
        assertEquals(1, graph.getProducedItems(ladderSource).size)
    }

    @Test
    fun `analyzeResourceSources provides correct statistics`() {
        val sources = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "stick.json",
                requiredItems = listOf(Item("minecraft:oak_planks", "Oak Planks")),
                producedItems = listOf(Item("minecraft:stick", "Stick"))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.LootTypes.CHEST,
                filename = "chest.json",
                requiredItems = emptyList(),
                producedItems = listOf(Item("minecraft:diamond", "Diamond"))
            ),
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.SMELTING,
                filename = "iron.json",
                requiredItems = listOf(Item("minecraft:raw_iron", "Raw Iron")),
                producedItems = listOf(Item("minecraft:iron_ingot", "Iron Ingot"))
            )
        )

        val stats = ItemSourceGraphBuilder.analyzeResourceSources(sources)

        assertEquals(3, stats["totalSources"])
        assertEquals(5, stats["uniqueItems"]) // planks, stick, diamond, raw_iron, iron_ingot
        assertEquals(2, stats["sourcesWithBoth"]) // crafting and smelting
        assertEquals(0, stats["sourcesWithInputsOnly"])
        assertEquals(1, stats["sourcesWithOutputsOnly"]) // chest
        assertEquals(0, stats["sourcesWithNeither"])

        @Suppress("UNCHECKED_CAST")
        val typeDistribution = stats["sourceTypeDistribution"] as Map<String, Int>
        assertEquals(1, typeDistribution[ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED.id])
        assertEquals(1, typeDistribution[ResourceSource.SourceType.LootTypes.CHEST.id])
        assertEquals(1, typeDistribution[ResourceSource.SourceType.RecipeTypes.SMELTING.id])
    }

    @Test
    fun `builds graph from real extracted Minecraft data`() {
        // Load actual extracted resource sources from test data
        val (_, sources) = TestUtils.executeAndAssertSuccess(
            ExtractResourceSources,
            MinecraftVersion.Release(1, 21, 11) to Path.of("src/test/resources/servers/extracted")
        )

        // Verify we have data
        assertTrue(sources.isNotEmpty(), "Expected extracted sources to be non-empty")

        // Build the graph
        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)

        // Verify graph was built successfully
        val stats = graph.getStatistics()
        assertTrue(stats["itemCount"]!! > 0, "Expected items in graph")
        assertTrue(stats["sourceCount"]!! > 0, "Expected sources in graph")
        assertTrue(stats["totalEdges"]!! > 0, "Expected edges in graph")

        println("Real data graph stats: $stats")

        // Verify we can query the graph and get detailed source information
        // Test with a known Minecraft item (sticks are almost always present)
        val stickSources = graph.getSourcesForItem("minecraft:stick")
        println("Found ${stickSources.size} sources for sticks")

        // Demonstrate that each source has its own filename and required items
        stickSources.take(3).forEach { source ->
            val requiredItems = graph.getRequiredItems(source)
            println("  - ${source.sourceType.name} (${source.filename}): requires ${requiredItems.size} items")
            requiredItems.take(2).forEach { item ->
                println("    * ${item.itemId}")
            }
        }

        // Analyze the sources
        val analysis = ItemSourceGraphBuilder.analyzeResourceSources(sources)
        println("Source analysis: $analysis")

        // Basic sanity checks on real data
        assertTrue(analysis["totalSources"] as Int > 0)
        assertTrue(analysis["uniqueItems"] as Int > 0)
    }

    @Test
    fun `handles complex recipe with multiple inputs`() {
        // Diamond pickaxe: 3 diamonds + 2 sticks
        val sources = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "diamond_pickaxe.json",
                requiredItems = listOf(
                    Item("minecraft:diamond", "Diamond"),
                    Item("minecraft:diamond", "Diamond"),
                    Item("minecraft:diamond", "Diamond"),
                    Item("minecraft:stick", "Stick"),
                    Item("minecraft:stick", "Stick")
                ),
                producedItems = listOf(Item("minecraft:diamond_pickaxe", "Diamond Pickaxe"))
            )
        )

        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)

        // Should have 3 unique items (diamond, stick, diamond_pickaxe)
        assertEquals(3, graph.getAllItems().size)

        // Verify the recipe requires both diamonds and sticks
        val source = graph.getAllSources().first()
        val required = graph.getRequiredItems(source)

        assertEquals(2, required.size) // Deduplicated: diamond and stick
        assertTrue(required.any { it.itemId == "minecraft:diamond" })
        assertTrue(required.any { it.itemId == "minecraft:stick" })
    }

    @Test
    fun `graph statistics match expected values`() {
        val sources = listOf(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = "stick.json",
                requiredItems = listOf(Item("minecraft:planks", "Planks")),
                producedItems = listOf(Item("minecraft:stick", "Stick"))
            )
        )

        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)
        val stats = graph.getStatistics()

        assertEquals(2, stats["itemCount"]) // planks, stick
        assertEquals(1, stats["sourceCount"])
        assertEquals(1, stats["itemToSourceEdges"]) // planks -> source
        assertEquals(1, stats["sourceToItemEdges"]) // source -> stick
        assertEquals(2, stats["totalEdges"])
    }
}

