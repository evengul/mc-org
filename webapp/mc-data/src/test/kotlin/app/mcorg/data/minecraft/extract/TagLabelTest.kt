package app.mcorg.data.minecraft.extract

import app.mcorg.data.minecraft.extract.recipe.choiceTag
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MCO-489: a variant question is named by the things it asks you to choose between. */
class TagLabelTest {

    private fun context(
        names: Map<String, String> = emptyMap(),
        tags: Map<String, List<String>> = emptyMap(),
    ) = ExtractionContext(
        version = MinecraftVersion.Release(1, 21, 4),
        root = Path.of("/tmp/test"),
        names = names,
        tags = tags,
        itemIds = emptySet(),
    )

    private fun source(vararg required: MinecraftTag) = ResourceSource(
        type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
        filename = "tnt.json",
        producedItems = listOf(Item("minecraft:tnt", "TNT") to ResourceQuantity.ItemQuantity(1)),
        requiredItems = required.map { it to ResourceQuantity.ItemQuantity(1) as ResourceQuantity },
    )

    private fun nameOfRequired(source: ResourceSource) = source.requiredItems.single().first.name

    // --- tagChoiceName ---

    @Test
    fun `a two-member choice names both members`() {
        assertEquals("Red Sand or Sand", tagChoiceName(listOf("minecraft:sand", "minecraft:red_sand")))
    }

    @Test
    fun `a three-member choice lists them`() {
        assertEquals(
            "Blackstone, Cobbled Deepslate or Cobblestone",
            tagChoiceName(listOf("minecraft:cobblestone", "minecraft:blackstone", "minecraft:cobbled_deepslate")),
        )
    }

    /** Order in, order out is not a thing: the label is a function of the set. */
    @Test
    fun `the label does not depend on the order the members arrive in`() {
        assertEquals(
            tagChoiceName(listOf("minecraft:sand", "minecraft:red_sand")),
            tagChoiceName(listOf("minecraft:red_sand", "minecraft:sand")),
        )
    }

    @Test
    fun `a long set summarises but still names two options, within the name column`() {
        val label = tagChoiceName(
            listOf("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray")
                .map { "minecraft:${it}_wool" }
        )!!

        assertEquals("8 options: Gray Wool, Light Blue Wool, …", label)
        assertTrue(label.length <= 100)
    }

    @Test
    fun `a tag with no members has no member-derived label`() {
        assertEquals(null, tagChoiceName(emptyList()))
    }

    // --- withNames ---

    /**
     * The regression MCO-486 introduced: canonicalising the sand/red_sand choice onto
     * `#minecraft:smelts_to_glass` used to relabel it "Smelts To Glass", a phrase naming neither
     * of the two blocks being chosen between.
     */
    @Test
    fun `a vanilla tag is named by its members, not by its id`() {
        val context = context(
            tags = mapOf("#minecraft:smelts_to_glass" to listOf("minecraft:sand", "minecraft:red_sand")),
            names = mapOf("minecraft:sand" to "Sand (Block)", "minecraft:red_sand" to "Red Sand (Block)"),
        )

        val named = source(MinecraftTag("#minecraft:smelts_to_glass", "Smelts To Glass", emptyList()))
            .withNames(context)

        assertEquals("Red Sand or Sand", nameOfRequired(named))
    }

    /** The whole point: the label survives the id changing under it. */
    @Test
    fun `a synthetic tag and the vanilla tag it folds onto read identically`() {
        val context = context(
            tags = mapOf("#minecraft:smelts_to_glass" to listOf("minecraft:sand", "minecraft:red_sand"))
        )

        val synthetic = source(choiceTag(listOf("minecraft:sand", "minecraft:red_sand"))).withNames(context)
        val vanilla = source(MinecraftTag("#minecraft:smelts_to_glass", "irrelevant", emptyList())).withNames(context)

        assertEquals(nameOfRequired(synthetic), nameOfRequired(vanilla))
        assertEquals("Red Sand or Sand", nameOfRequired(vanilla))
    }

    @Test
    fun `member display names still come from the catalog`() {
        val context = context(
            tags = mapOf("#minecraft:coals" to listOf("minecraft:coal", "minecraft:charcoal")),
            names = mapOf("minecraft:coal" to "Coal (Item)", "minecraft:charcoal" to "Charcoal (Item)"),
        )

        val named = source(MinecraftTag("#minecraft:coals", "Coals", emptyList())).withNames(context)
        val tag = named.requiredItems.single().first as MinecraftTag

        // The label is sorted (it names a set); the members keep the registry's own order.
        assertEquals("Charcoal or Coal", tag.name)
        assertEquals(listOf("Coal (Item)", "Charcoal (Item)"), tag.content.map { it.name })
    }

    /** Nothing to name it by, so the id is the last resort — unchanged behaviour. */
    @Test
    fun `a tag with no members anywhere falls back to its id`() {
        val named = source(MinecraftTag("#minecraft:wooden_slabs", "whatever", emptyList())).withNames(context())

        assertEquals("Wooden Slabs", nameOfRequired(named))
    }
}
