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
    fun `fluids and placed effects are flagged as non-materials, ahead of any graph verdict`() {
        val water = item("water")
        val fire = item("fire")

        val warnings = classifyImportWarnings(
            mapOf(water to 10, fire to 2),
            graphProducing(water, fire),
        )

        assertEquals(ImportWarningKind.NON_MATERIAL, warnings.forItem(water.id)?.kind)
        assertEquals(ImportWarningKind.NON_MATERIAL, warnings.forItem(fire.id)?.kind)
    }

    @Test
    fun `powder snow reads as a non-material, not as creative-only`() {
        // MCO-319: powder_snow is a block-only id placed from a bucket, so no source produces
        // it and the graph rule alone would call it creative-only — the wrong story for a
        // block any player can place. It is the same shape as water and gets water's answer.
        // The graph here produces nothing, which is exactly the real-world condition.
        val powderSnow = item("powder_snow")

        val warnings = classifyImportWarnings(mapOf(powderSnow to 2), graphProducing(item("stone")))

        assertEquals(ImportWarningKind.NON_MATERIAL, warnings.forItem(powderSnow.id)?.kind)
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
        // categories still apply, because they need no graph to be true.
        val warnings = classifyImportWarnings(
            mapOf(item("command_block") to 1, item("water") to 1, item("elytra") to 1),
            null,
        )

        assertNull(warnings.forItem("minecraft:command_block"))
        assertEquals(ImportWarningKind.NON_MATERIAL, warnings.forItem("minecraft:water")?.kind)
        assertEquals(ImportWarningKind.EXPENSIVE, warnings.forItem("minecraft:elytra")?.kind)
    }

    @Test
    fun `warnings of a kind come back largest first`() {
        val warnings = classifyImportWarnings(
            mapOf(item("water") to 5, item("lava") to 900, item("fire") to 60),
            null,
        )

        assertEquals(
            listOf("minecraft:lava", "minecraft:fire", "minecraft:water"),
            warnings.of(ImportWarningKind.NON_MATERIAL).map { it.item.id },
        )
    }

    @Test
    fun `air is not classified here at all — it is filtered before the list is built`() {
        // NON_MATERIAL_FILL exists so both import doors drop air outright; if it ever reached
        // the review screen anyway, it should still read as a non-material rather than silently.
        assertTrue("minecraft:air" in NON_MATERIAL_FILL)
        assertEquals(
            ImportWarningKind.NON_MATERIAL,
            classifyImportWarnings(mapOf(item("air") to 9_389_854), null).forItem("minecraft:air")?.kind,
        )
    }
}
