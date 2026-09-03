package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.data.minecraft.extract.ExtractionContext
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ChoiceTagCanonicaliserTest {

    private fun context(tags: Map<String, List<String>>) = ExtractionContext(
        version = MinecraftVersion.Release(1, 21, 4),
        root = Path.of("/tmp/test"),
        names = emptyMap(),
        tags = tags,
        itemIds = emptySet(),
    )

    private val coalTags = mapOf(
        "#minecraft:coals" to listOf("minecraft:coal", "minecraft:charcoal"),
        "#minecraft:planks" to listOf("minecraft:oak_planks", "minecraft:spruce_planks"),
    )

    private fun source(vararg required: Pair<MinecraftId, Int>) = ResourceSource(
        type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
        filename = "torch.json",
        producedItems = listOf(Item("minecraft:torch", "Torch") to ResourceQuantity.ItemQuantity(4)),
        requiredItems = required.map { (id, count) -> id to ResourceQuantity.ItemQuantity(count) },
    )

    @Test
    fun `a generated set matching a vanilla tag resolves to the vanilla id`() {
        val canonicaliser = ChoiceTagCanonicaliser.from(context(coalTags))

        val canonical = canonicaliser.canonicalise(choiceTag(listOf("minecraft:coal", "minecraft:charcoal")))

        assertEquals("#minecraft:coals", canonical.id)
        // Only the id changes. The vanilla tag's own name ("Coals") describes what Mojang uses it
        // for and names neither option; the question keeps the label its members gave it (MCO-489).
        assertEquals("Charcoal or Coal", canonical.name)
        assertEquals(
            listOf("minecraft:charcoal", "minecraft:coal"),
            (canonical as MinecraftTag).content.map { it.id },
        )
    }

    @Test
    fun `a generated set no vanilla tag names keeps its synthetic id`() {
        val canonicaliser = ChoiceTagCanonicaliser.from(context(coalTags))

        val canonical = canonicaliser.canonicalise(choiceTag(listOf("minecraft:sand", "minecraft:red_sand")))

        assertEquals("#mcorg:choice/red_sand_sand", canonical.id)
    }

    @Test
    fun `a partial overlap is not a match — only an exact member set folds`() {
        val canonicaliser = ChoiceTagCanonicaliser.from(
            context(mapOf("#minecraft:planks" to listOf("minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks")))
        )

        val canonical = canonicaliser.canonicalise(choiceTag(listOf("minecraft:oak_planks", "minecraft:spruce_planks")))

        assertEquals("#mcorg:choice/oak_planks_spruce_planks", canonical.id)
    }

    @Test
    fun `vanilla duplicating vanilla is left alone — that half is the planner's`() {
        // Mojang ships #planks and #wooden_tool_materials with identical contents. Rewriting one
        // to the other would change what a recipe says it wants; TagIdentity folds them into one
        // question at plan time instead.
        val canonicaliser = ChoiceTagCanonicaliser.from(
            context(
                mapOf(
                    "#minecraft:planks" to listOf("minecraft:oak_planks", "minecraft:spruce_planks"),
                    "#minecraft:wooden_tool_materials" to listOf("minecraft:oak_planks", "minecraft:spruce_planks"),
                )
            )
        )

        val named = MinecraftTag("#minecraft:wooden_tool_materials", "Wooden Tool Materials", emptyList())

        assertEquals("#minecraft:wooden_tool_materials", canonicaliser.canonicalise(named).id)
    }

    @Test
    fun `a set reached through a nested tag reference still matches`() {
        val canonicaliser = ChoiceTagCanonicaliser.from(
            context(
                mapOf(
                    "#minecraft:coals" to listOf("#minecraft:vanilla_coals"),
                    "#minecraft:vanilla_coals" to listOf("minecraft:coal", "minecraft:charcoal"),
                )
            )
        )

        val canonical = canonicaliser.canonicalise(choiceTag(listOf("minecraft:coal", "minecraft:charcoal")))

        // Both vanilla tags resolve to the same two members; the smaller id wins, deterministically.
        assertEquals("#minecraft:coals", canonical.id)
    }

    /**
     * MCO-488: `#minecraft:swords` and `#minecraft:enchantable/sword` hold the same items from
     * 1.20.5 on, and the nested one sorts first alphabetically. It names an enchantment slot, not
     * the set, so the top-level tag has to win — five such pairs exist per version.
     */
    @Test
    fun `a top-level tag beats a nested one holding the same set`() {
        val canonicaliser = ChoiceTagCanonicaliser.from(
            context(
                mapOf(
                    "#minecraft:swords" to listOf("minecraft:iron_sword", "minecraft:stone_sword"),
                    "#minecraft:enchantable/sword" to listOf("#minecraft:swords"),
                )
            )
        )

        val canonical = canonicaliser.canonicalise(choiceTag(listOf("minecraft:iron_sword", "minecraft:stone_sword")))

        assertEquals("#minecraft:swords", canonical.id)
    }

    /**
     * The registry-wide walk is what found MCO-488: `enchantable/foot_armor.json`'s only value is
     * `#minecraft:foot_armor`, and keyed by base filename that entry was itself. Building the
     * canonicaliser over such a registry used to blow the stack.
     */
    @Test
    fun `building over a self-referential tag terminates`() {
        val canonicaliser = ChoiceTagCanonicaliser.from(
            context(
                mapOf(
                    "#minecraft:foot_armor" to listOf("#minecraft:foot_armor"),
                    "#minecraft:coals" to listOf("minecraft:coal", "minecraft:charcoal"),
                )
            )
        )

        assertEquals(
            "#minecraft:coals",
            canonicaliser.canonicalise(choiceTag(listOf("minecraft:coal", "minecraft:charcoal"))).id,
        )
    }

    @Test
    fun `canonicalising a source rewrites its consumed choice tags`() {
        val canonicaliser = ChoiceTagCanonicaliser.from(context(coalTags))

        val canonical = canonicaliser.canonicalise(
            source(
                choiceTag(listOf("minecraft:coal", "minecraft:charcoal")) to 1,
                Item("minecraft:stick", "Stick") to 1,
            )
        )

        assertEquals(
            listOf("#minecraft:coals", "minecraft:stick"),
            canonical.requiredItems.map { it.first.id },
        )
    }

    @Test
    fun `two slots that canonicalise to the same tag merge and sum`() {
        val canonicaliser = ChoiceTagCanonicaliser.from(context(coalTags))

        val canonical = canonicaliser.canonicalise(
            source(
                MinecraftTag("#minecraft:coals", "Coals", emptyList()) to 2,
                choiceTag(listOf("minecraft:coal", "minecraft:charcoal")) to 3,
            )
        )

        assertEquals(1, canonical.requiredItems.size)
        assertEquals("#minecraft:coals", canonical.requiredItems.single().first.id)
        assertEquals(ResourceQuantity.ItemQuantity(5), canonical.requiredItems.single().second)
    }

    @Test
    fun `a source with no choice tags is returned unchanged`() {
        val canonicaliser = ChoiceTagCanonicaliser.from(context(coalTags))
        val original = source(Item("minecraft:stick", "Stick") to 1)

        assertEquals(original, canonicaliser.canonicalise(original))
    }
}
