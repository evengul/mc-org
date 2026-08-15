package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.ItemSourceGraph
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-305 — the rules behind the review screen's warning strip, without a world attached.
 */
class ImportWarningsTest {

    private val block = ResourceSource.SourceType.LootTypes.BLOCK

    private fun item(name: String) = Item("minecraft:$name", name.replaceFirstChar { it.uppercase() })

    /** A graph in which every listed id has exactly one block-loot producer. */
    private fun graphProducing(vararg items: MinecraftId): ItemSourceGraph {
        val builder = ItemSourceGraph.builder()
        items.forEach { produced ->
            val source = builder.addSourceNode(block, "blocks/${produced.id.substringAfter(':')}.json")
            builder.addSourceToItemEdge(source, builder.addItemNode(produced), 1)
        }
        return builder.build()
    }

    @Test
    fun `an id no source produces is flagged as creative-only`() {
        val commandBlock = item("command_block")
        val stone = item("stone")

        val warnings = classifyImportWarnings(
            mapOf(commandBlock to 4, stone to 100),
            graphProducing(stone),
        )

        assertEquals(ImportWarningKind.UNOBTAINABLE, warnings.forItem(commandBlock.id)?.kind)
        assertNull(warnings.forItem(stone.id), "an ordinary block with a source is not worth a word")
    }

    @Test
    fun `an obtainable-but-painful id is flagged as slow to gather`() {
        val skull = item("wither_skeleton_skull")

        val warnings = classifyImportWarnings(mapOf(skull to 3), graphProducing(skull))

        assertEquals(ImportWarningKind.EXPENSIVE, warnings.forItem(skull.id)?.kind)
    }

    @Test
    fun `graph truth outranks the curated expensive list`() {
        val skull = item("wither_skeleton_skull")

        // Same curated id, but this version's graph has no source for it at all. "Creative
        // only" is the more useful thing to say than "slow to gather".
        val warnings = classifyImportWarnings(mapOf(skull to 3), graphProducing(item("stone")))

        assertEquals(ImportWarningKind.UNOBTAINABLE, warnings.forItem(skull.id)?.kind)
    }

    @Test
    fun `there is no non-material warning kind to fall back on`() {
        // MCO-396: the placed-but-not-gathered ids (fluids, portals, effect blocks) are resolved
        // or dropped in PlacedBlocks before anything reaches this classifier, so there is
        // nothing left for a third kind to describe. Warning about a row *and* keeping it is
        // what put water and nether portals in the plan as permanently blocked nodes.
        assertEquals(
            listOf(ImportWarningKind.UNOBTAINABLE, ImportWarningKind.EXPENSIVE),
            ImportWarningKind.entries.toList(),
        )
    }

    @Test
    fun `a bucket is an ordinary craftable row, warned about by nothing`() {
        // What a fluid cell now arrives as. A bucket has a recipe, so the screen says nothing.
        val bucket = item("water_bucket")

        val warnings = classifyImportWarnings(mapOf(bucket to 1), graphProducing(bucket))

        assertTrue(warnings.isEmpty)
    }

    @Test
    fun `an empty cauldron is not warned about at all`() {
        // The other half of MCO-319: filled cauldron states are redirected to minecraft:cauldron
        // by the importers, and a cauldron has a recipe, so nothing should be said about it.
        val cauldron = item("cauldron")

        val warnings = classifyImportWarnings(mapOf(cauldron to 6), graphProducing(cauldron))

        assertTrue(warnings.isEmpty)
    }

    @Test
    fun `a name mismatch between catalog and graph does not fake an unobtainable row`() {
        // The graph node carries one display name and the world catalog another. Matching on
        // Item equality (id *and* name) would report a perfectly craftable item as blocked.
        val graph = graphProducing(Item("minecraft:oak_planks", "Oak Wood Planks"))

        val warnings = classifyImportWarnings(mapOf(Item("minecraft:oak_planks", "Oak Planks") to 64), graph)

        assertTrue(warnings.isEmpty)
    }

    @Test
    fun `no graph means no unobtainable claims`() {
        // A world whose version was never ingested. Silence beats guessing; the curated
        // category still applies, because it needs no graph to be true.
        val warnings = classifyImportWarnings(
            mapOf(item("command_block") to 1, item("elytra") to 1),
            null,
        )

        assertNull(warnings.forItem("minecraft:command_block"))
        assertEquals(ImportWarningKind.EXPENSIVE, warnings.forItem("minecraft:elytra")?.kind)
    }

    @Test
    fun `warnings of a kind come back largest first`() {
        val warnings = classifyImportWarnings(
            mapOf(item("elytra") to 5, item("nether_star") to 900, item("trident") to 60),
            null,
        )

        assertEquals(
            listOf("minecraft:nether_star", "minecraft:trident", "minecraft:elytra"),
            warnings.of(ImportWarningKind.EXPENSIVE).map { it.item.id },
        )
    }
}
