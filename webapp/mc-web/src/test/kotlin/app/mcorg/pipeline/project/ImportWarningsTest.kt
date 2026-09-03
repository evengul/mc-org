package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.engine.model.ItemSourceGraph
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
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
        // nothing left for a kind of that shape to describe. Warning about a row *and* keeping
        // it is what put water and nether portals in the plan as permanently blocked nodes.
        //
        // MCO-321 added LIMITED_SUPPLY, and deliberately did *not* add a "needs a duplication
        // trick" kind next to it: it would have exactly the same members, since needing to dupe
        // is the consequence of the cap rather than a separate fact, and the other duplication
        // in these builds (TNT) has no material row for a warning to hang on.
        assertEquals(
            listOf(
                ImportWarningKind.UNOBTAINABLE,
                ImportWarningKind.LIMITED_SUPPLY,
                ImportWarningKind.EXPENSIVE,
            ),
            ImportWarningKind.entries.toList(),
        )
    }

    @Test
    fun `a dragon egg is flagged as limited supply, not left silent`() {
        // MCO-321, from a real import: "BEN RK World Eater.litematic" asks for 55 dragon eggs,
        // one per TNT duper. A dragon egg has an ordinary block loot table, so the graph sees a
        // source and the old classifier said nothing at all — the plan then told the user to go
        // and break 55 of them.
        val egg = item("dragon_egg")

        val warnings = classifyImportWarnings(mapOf(egg to 55), graphProducing(egg))

        val warning = warnings.forItem(egg.id)
        assertEquals(ImportWarningKind.LIMITED_SUPPLY, warning?.kind)
        assertEquals(55, warning?.amount)
    }

    @Test
    fun `the limited-supply warning states the cap and the duplication it implies`() {
        // The whole value of this category is the sentence, not the chip: the user needs to
        // know *what kind* of problem this is before they start gathering.
        val warnings = classifyImportWarnings(mapOf(item("dragon_egg") to 50), null)

        val message = warnings.forItem("minecraft:dragon_egg")?.message.orEmpty()
        assertContains(message, "exactly one")
        assertContains(message, "first dragon")
        assertContains(message, "duplication")
    }

    @Test
    fun `an ordinary warning carries no item-specific detail`() {
        // Only the capped kind has a per-item reason; everything else falls back to the
        // category's own explanation, which is what the chip's hover text shows.
        val warning = classifyImportWarnings(mapOf(item("elytra") to 3), null).forItem("minecraft:elytra")

        assertNull(warning?.detail)
        assertEquals(ImportWarningKind.EXPENSIVE.explanation, warning?.message)
    }

    @Test
    fun `a hard cap outranks a graph that happens to produce the item`() {
        // The mirror of the creative-only precedence test. A version whose graph has no dragon
        // egg source at all is genuinely creative-only and should say so...
        val eggless = classifyImportWarnings(mapOf(item("dragon_egg") to 55), graphProducing(item("stone")))
        assertEquals(ImportWarningKind.UNOBTAINABLE, eggless.forItem("minecraft:dragon_egg")?.kind)

        // ...but with no graph to consult, the curated cap still holds: it is a fact about
        // Minecraft, not about what this world's version happens to have ingested.
        val ungraphed = classifyImportWarnings(mapOf(item("dragon_egg") to 55), null)
        assertEquals(ImportWarningKind.LIMITED_SUPPLY, ungraphed.forItem("minecraft:dragon_egg")?.kind)
    }

    @Test
    fun `the limited-supply set stays as small as the vanilla facts allow`() {
        // Curation guard. The bar is a hard per-world cap, not scarcity — an elytra comes from
        // structures an infinite world generates infinitely many of, and a nether star from a
        // farmable mob. Both are grinds and belong in the expensive list; neither may drift
        // into this one without someone changing this test on purpose.
        val candidates = mapOf(
            item("elytra") to 1,
            item("nether_star") to 1,
            item("heart_of_the_sea") to 1,
            item("echo_shard") to 1,
            item("totem_of_undying") to 1,
        )

        val warnings = classifyImportWarnings(candidates, null)

        assertTrue(
            warnings.of(ImportWarningKind.LIMITED_SUPPLY).isEmpty(),
            "renewable or unlimited-per-world items are grinds, not caps",
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
