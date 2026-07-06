package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.engine.model.ItemSourceGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VariantDiscoveryTest {

    private fun item(id: String, name: String) = Item("minecraft:$id", name)

    @Test
    fun `returns the other members of a tag family sorted by name`() {
        val builder = ItemSourceGraph.builder()
        builder.addItemNode(
            MinecraftTag(
                "#minecraft:planks", "Planks",
                listOf(item("oak_planks", "Oak Planks"), item("spruce_planks", "Spruce Planks"), item("birch_planks", "Birch Planks"))
            )
        )
        val graph = builder.build()

        val candidates = findVariantCandidates(graph, "minecraft:birch_planks")

        assertEquals(listOf("Oak Planks", "Spruce Planks"), candidates.map { it.name })
    }

    @Test
    fun `excludes the item itself from its own candidate list`() {
        val builder = ItemSourceGraph.builder()
        builder.addItemNode(
            MinecraftTag("#minecraft:wool", "Wool", listOf(item("white_wool", "White Wool"), item("red_wool", "Red Wool")))
        )
        val graph = builder.build()

        val candidates = findVariantCandidates(graph, "minecraft:white_wool")

        assertTrue(candidates.none { it.id == "minecraft:white_wool" })
    }

    @Test
    fun `an item with no covering tag in the graph has no candidates`() {
        val builder = ItemSourceGraph.builder()
        builder.addItemNode(
            MinecraftTag("#minecraft:planks", "Planks", listOf(item("oak_planks", "Oak Planks"), item("spruce_planks", "Spruce Planks")))
        )
        val graph = builder.build()

        // "birch_door" belongs to no tag present in this graph (vanilla has no "any door"
        // recipe ingredient, so a #minecraft:doors tag never becomes a graph node — MCO-246).
        val candidates = findVariantCandidates(graph, "minecraft:birch_door")

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `a null graph yields no candidates`() {
        assertTrue(findVariantCandidates(null, "minecraft:birch_planks").isEmpty())
    }

    @Test
    fun `candidates from multiple containing tags are deduplicated by id`() {
        val builder = ItemSourceGraph.builder()
        // Two tags that both happen to include spruce_planks alongside the target item.
        builder.addItemNode(
            MinecraftTag("#minecraft:planks", "Planks", listOf(item("oak_planks", "Oak Planks"), item("spruce_planks", "Spruce Planks")))
        )
        builder.addItemNode(
            MinecraftTag("#mcorg:choice/oak_spruce", "Oak or Spruce", listOf(item("oak_planks", "Oak Planks"), item("spruce_planks", "Spruce Planks")))
        )
        val graph = builder.build()

        val candidates = findVariantCandidates(graph, "minecraft:oak_planks")

        assertEquals(listOf("minecraft:spruce_planks"), candidates.map { it.id })
    }

    @Test
    fun `an item with no tags at all in an otherwise non-empty graph has no candidates`() {
        val builder = ItemSourceGraph.builder()
        builder.addItemNode(item("iron_ingot", "Iron Ingot"))
        val graph = builder.build()

        assertTrue(findVariantCandidates(graph, "minecraft:iron_ingot").isEmpty())
    }
}
