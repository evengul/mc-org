package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MCO-304 — family detection and batch swapping, against a catalog shaped like the real one.
 */
class ItemFamiliesTest {

    private val woods = listOf(
        "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry",
        "bamboo", "crimson", "warped", "dark_oak", "pale_oak",
    )
    private val colours = listOf(
        "white", "orange", "magenta", "yellow", "lime", "pink", "gray", "cyan",
        "purple", "blue", "brown", "green", "red", "black", "light_blue", "light_gray",
    )

    /** A catalog with the shape that matters: two families, one of them with two-token members. */
    private val catalog: List<Item> = buildList {
        woods.forEach { wood ->
            add(item("${wood}_planks"))
            add(item("${wood}_slab"))
            add(item("${wood}_stairs"))
        }
        // Not every species has a sapling — crimson and warped grow from fungi.
        woods.filterNot { it == "crimson" || it == "warped" }.forEach { add(item("${it}_sapling")) }
        colours.forEach { colour ->
            add(item("${colour}_terracotta"))
            add(item("${colour}_wool"))
            add(item("${colour}_stained_glass_pane"))
        }
        add(item("terracotta"))
        add(item("glass_pane"))
        add(item("stone"))
        add(item("cobblestone"))
    }

    private fun item(name: String) =
        Item("minecraft:$name", name.split('_').joinToString(" ") { p -> p.replaceFirstChar { it.uppercase() } })

    private fun id(name: String) = "minecraft:$name"

    // ---- vocabulary ----------------------------------------------------------------

    @Test
    fun `the catalog spells out both families without being told about either`() {
        val tokens = variantTokens(catalog)

        assertTrue(woods.all { it in tokens }, "every wood species is a token: $tokens")
        assertTrue(colours.all { it in tokens }, "every dye colour is a token: $tokens")
    }

    @Test
    fun `two-token variants are found even though a single-token split hides them`() {
        // dark_oak_planks keyed on one token lands under `oak_planks`, where its only company
        // is pale_oak — too small to be a family. The second pass files it under `planks`.
        val tokens = variantTokens(catalog)

        assertTrue("dark_oak" in tokens)
        assertTrue("pale_oak" in tokens)
        assertTrue("light_blue" in tokens)
        assertTrue("light_gray" in tokens)
    }

    @Test
    fun `the second pass does not invent a compound colour token`() {
        // white_stained_glass_pane already belongs to a full family under `stained_glass_pane`,
        // so it must not also be filed as `white_stained` under `glass_pane` — that would split
        // white wool and white panes into two different "white" families.
        val tokens = variantTokens(catalog)

        assertTrue(colours.none { "${it}_stained" in tokens }, "compound tokens leaked: $tokens")
    }

    @Test
    fun `an empty catalog yields no vocabulary rather than an exception`() {
        assertTrue(variantTokens(emptyList()).isEmpty())
    }

    // ---- families ------------------------------------------------------------------

    @Test
    fun `rows sharing a species become one family`() {
        val families = findSubstitutionFamilies(
            catalog,
            listOf(id("oak_planks"), id("oak_stairs"), id("oak_slab"), id("stone")),
        )

        val oak = families.single()
        assertEquals("oak", oak.token)
        assertEquals("Oak", oak.label)
        assertEquals(listOf(id("oak_planks"), id("oak_stairs"), id("oak_slab")), oak.itemIds)
        assertTrue(oak.targets.any { it.token == "spruce" })
    }

    @Test
    fun `a two-token species reads as itself, not as its second word`() {
        val families = findSubstitutionFamilies(catalog, listOf(id("dark_oak_planks"), id("dark_oak_slab")))

        assertEquals("dark_oak", families.single().token)
        assertEquals("Dark Oak", families.single().label)
    }

    @Test
    fun `a colour family works even though vanilla has no terracotta tag`() {
        // The issue's headline example, and the reason this is derived from the catalog rather
        // than from tags: there is no "any terracotta" ingredient anywhere in vanilla.
        val families = findSubstitutionFamilies(catalog, listOf(id("blue_terracotta"), id("blue_wool")))

        val blue = families.single()
        assertEquals("blue", blue.token)
        assertTrue(blue.targets.any { it.token == "red" })
    }

    @Test
    fun `a target that cannot take every row is not offered`() {
        // Crimson has planks and a slab but no sapling, so "all oak to crimson" would have to
        // leave a row behind. Spruce takes all three.
        val families = findSubstitutionFamilies(
            catalog,
            listOf(id("oak_planks"), id("oak_slab"), id("oak_sapling")),
        )

        val targets = families.single().targets.map { it.token }
        assertTrue("spruce" in targets)
        assertTrue("crimson" !in targets, "crimson has no sapling: $targets")
    }

    @Test
    fun `a single row is not a batch and gets no family`() {
        assertTrue(findSubstitutionFamilies(catalog, listOf(id("oak_planks"), id("stone"))).isEmpty())
    }

    @Test
    fun `a list with nothing in common offers nothing`() {
        assertTrue(findSubstitutionFamilies(catalog, listOf(id("stone"), id("cobblestone"))).isEmpty())
    }

    // ---- applying ------------------------------------------------------------------

    @Test
    fun `swapping rewrites every row in the family and leaves the rest alone`() {
        val requirements = mapOf(
            item("oak_planks") to 64,
            item("oak_stairs") to 12,
            item("stone") to 100,
        )

        val swapped = applySubstitution(catalog, requirements, fromToken = "oak", toToken = "spruce")

        assertEquals(
            mapOf(id("spruce_planks") to 64, id("spruce_stairs") to 12, id("stone") to 100),
            swapped.mapKeys { it.key.id },
        )
    }

    @Test
    fun `swapping onto an id the list already has merges the two rows`() {
        val requirements = mapOf(
            item("oak_planks") to 64,
            item("spruce_planks") to 10,
        )

        val swapped = applySubstitution(catalog, requirements, fromToken = "oak", toToken = "spruce")

        assertEquals(mapOf(id("spruce_planks") to 74), swapped.mapKeys { it.key.id })
    }

    @Test
    fun `a row with no counterpart under the target is kept, never dropped`() {
        // Reachable only by a hand-rolled post — the UI does not offer crimson here — but the
        // total quantity must survive whatever arrives.
        val requirements = mapOf(item("oak_planks") to 64, item("oak_sapling") to 4)

        val swapped = applySubstitution(catalog, requirements, fromToken = "oak", toToken = "crimson")

        assertEquals(
            mapOf(id("crimson_planks") to 64, id("oak_sapling") to 4),
            swapped.mapKeys { it.key.id },
        )
        assertEquals(68, swapped.values.sum())
    }

    @Test
    fun `a swap the list has nothing to do with changes nothing`() {
        val requirements = mapOf(item("stone") to 5)

        assertEquals(requirements, applySubstitution(catalog, requirements, "oak", "spruce"))
    }

    @Test
    fun `dark oak is not caught by an oak swap`() {
        val requirements = mapOf(item("oak_planks") to 1, item("dark_oak_planks") to 1)

        val swapped = applySubstitution(catalog, requirements, fromToken = "oak", toToken = "spruce")

        assertEquals(
            mapOf(id("spruce_planks") to 1, id("dark_oak_planks") to 1),
            swapped.mapKeys { it.key.id },
        )
    }

    @Test
    fun `light blue is not caught by a blue swap`() {
        val requirements = mapOf(item("blue_wool") to 1, item("light_blue_wool") to 1)

        val swapped = applySubstitution(catalog, requirements, fromToken = "blue", toToken = "red")

        assertEquals(
            mapOf(id("red_wool") to 1, id("light_blue_wool") to 1),
            swapped.mapKeys { it.key.id },
        )
    }

    @Test
    fun `an id with no namespace is ignored rather than mis-split`() {
        assertNull(findSubstitutionFamilies(catalog, listOf("oak_planks", "oak_slab")).firstOrNull())
    }
}
