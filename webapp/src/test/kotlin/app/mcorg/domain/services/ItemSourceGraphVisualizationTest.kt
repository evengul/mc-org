package app.mcorg.domain.services

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.minecraft.extract.ExtractResourceSources
import app.mcorg.test.utils.TestUtils
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path

/**
 * Test to generate one-time visualizations of the item source graph.
 *
 * Run this test to generate visualization files that can be viewed:
 * - .dot files: Open at https://dreampuf.github.io/GraphvizOnline/
 * - .txt files: View in any text editor
 */
class ItemSourceGraphVisualizationTest {

    @Test
    fun `generate DOT visualization of small subset`() {
        // Use test data from setup
        val (_, sources) = TestUtils.executeAndAssertSuccess(
            ExtractResourceSources,
            MinecraftVersion.Release(1, 21, 11) to Path.of("src/test/resources/servers/extracted")
        )

        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)

        // Generate DOT file (limited to 30 items for readability)
        val dot = ItemSourceGraphExporter.toDot(graph, maxItems = 30, maxSourcesPerItem = 3)

        // Save to file
        val outputFile = File("target/item-graph-subset.dot")
        outputFile.writeText(dot)

        println("✅ Generated DOT visualization: ${outputFile.absolutePath}")
        println("📊 View online: https://dreampuf.github.io/GraphvizOnline/")
        println("📊 Or render locally: dot -Tpng ${outputFile.name} -o item-graph-subset.png")
        println()
        println("First 200 chars of DOT:")
        println(dot.take(200))
    }

    @Test
    fun `generate production chain for diamond pickaxe`() {
        val (_, sources) = TestUtils.executeAndAssertSuccess(
            ExtractResourceSources,
            MinecraftVersion.Release(1, 21, 11) to Path.of("src/test/resources/servers/extracted")
        )

        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)

        // Generate production chain for diamond pickaxe
        val dot = ItemSourceGraphExporter.toProductionChainDot(
            graph,
            "minecraft:diamond_pickaxe",
            maxDepth = 4
        )

        val outputFile = File("target/diamond-pickaxe-chain.dot")
        outputFile.writeText(dot)

        println("✅ Generated production chain: ${outputFile.absolutePath}")
        println("📊 View online: https://dreampuf.github.io/GraphvizOnline/")
        println()
        println("First 300 chars of DOT:")
        println(dot.take(300))
    }

    @Test
    fun `generate production chain for sticks`() {
        val (_, sources) = TestUtils.executeAndAssertSuccess(
            ExtractResourceSources,
            MinecraftVersion.Release(1, 21, 11) to Path.of("src/test/resources/servers/extracted")
        )

        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)

        val dot = ItemSourceGraphExporter.toProductionChainDot(
            graph,
            "minecraft:stick",
            maxDepth = 3
        )

        val outputFile = File("target/stick-chain.dot")
        outputFile.writeText(dot)

        println("✅ Generated stick production chain: ${outputFile.absolutePath}")
        println("📊 Shows all ${graph.getSourcesForItem("minecraft:stick").size} ways to get sticks!")
    }

    @Test
    fun `generate text summary`() {
        val (_, sources) = TestUtils.executeAndAssertSuccess(
            ExtractResourceSources,
            MinecraftVersion.Release(1, 21, 11) to Path.of("src/test/resources/servers/extracted")
        )

        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)

        val text = ItemSourceGraphExporter.toText(graph, maxItems = 50)

        val outputFile = File("target/graph-summary.txt")
        outputFile.writeText(text)

        println("✅ Generated text summary: ${outputFile.absolutePath}")
        println()
        println("Preview:")
        println(text.lines().take(30).joinToString("\n"))
    }

    @Test
    fun `generate production chains for common items`() {
        val (_, sources) = TestUtils.executeAndAssertSuccess(
            ExtractResourceSources,
            MinecraftVersion.Release(1, 21, 11) to Path.of("src/test/resources/servers/extracted")
        )

        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)

        // Generate chains for several useful items
        val items = listOf(
            "minecraft:crafting_table",
            "minecraft:iron_pickaxe",
            "minecraft:chest",
            "minecraft:furnace",
            "minecraft:diamond_sword"
        )

        items.forEach { itemId ->
            val itemName = itemId.removePrefix("minecraft:")
            val dot = ItemSourceGraphExporter.toProductionChainDot(graph, itemId, maxDepth = 4)

            val outputFile = File("target/$itemName-chain.dot")
            outputFile.writeText(dot)

            println("✅ Generated: $itemName-chain.dot")
        }

        println()
        println("All files saved to: target/")
        println("View at: https://dreampuf.github.io/GraphvizOnline/")
    }

    @Test
    fun `analyze leaf and top level items`() {
        val (_, sources) = TestUtils.executeAndAssertSuccess(
            ExtractResourceSources,
            MinecraftVersion.Release(1, 21, 11) to Path.of("src/test/resources/servers/extracted")
        )

        val graph = ItemSourceGraphBuilder.buildFromResourceSources(sources)
        val queries = ItemSourceGraphQueries(graph)

        val leafItems = queries.findLeafItems()
        val topLevelItems = queries.findTopLevelItems()

        val output = buildString {
            appendLine("=".repeat(60))
            appendLine("GRAPH ANALYSIS")
            appendLine("=".repeat(60))
            appendLine()

            appendLine("LEAF ITEMS (Base Resources - can only be found/mined):")
            appendLine("-".repeat(60))
            leafItems.take(50).forEach { item ->
                appendLine("  • ${item.itemId}")
            }
            if (leafItems.size > 50) {
                appendLine("  ... and ${leafItems.size - 50} more")
            }

            appendLine()
            appendLine("TOP-LEVEL ITEMS (Final Products - not used in other recipes):")
            appendLine("-".repeat(60))
            topLevelItems.take(50).forEach { item ->
                appendLine("  • ${item.itemId}")
            }
            if (topLevelItems.size > 50) {
                appendLine("  ... and ${topLevelItems.size - 50} more")
            }

            appendLine()
            appendLine("STATISTICS:")
            appendLine("-".repeat(60))
            val analysis = queries.analyzeGraph()
            analysis.forEach { (key, value) ->
                appendLine("  $key: $value")
            }
        }

        val outputFile = File("target/graph-analysis.txt")
        outputFile.writeText(output)

        println("✅ Generated analysis: ${outputFile.absolutePath}")
        println()
        println(output.lines().take(40).joinToString("\n"))
    }
}

