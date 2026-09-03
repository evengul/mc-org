package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.resources.ResourceGatheringItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins that the resource list actually draws item glyphs (MCO-499).
 *
 * MCO-479 shipped the whole glyph system — 73 rules, a DSL renderer, a stylesheet, a fallback
 * mark — and nothing called any of it for weeks. `ItemGlyphTest` was green the entire time,
 * because it tests the rules, not their use. This class tests the use: it is the thing that
 * fails if a template edit drops the call and turns the set back into dead code.
 */
class PlanResourceTableGlyphTest {

    private var nextId = 1

    private fun row(itemId: String, name: String, required: Int = 64, ignored: Boolean = false) =
        ResourceGatheringItem(
            id = nextId++,
            projectId = 1,
            itemId = itemId,
            name = name,
            required = required,
            collected = 0,
            ignored = ignored,
        )

    private fun render(vararg rows: ResourceGatheringItem) =
        planResourcesAreaFragment(worldId = 1, projectId = 1, resources = rows.toList(), plan = null)

    @Test
    fun `every visible row carries a glyph`() {
        val html = render(
            row("minecraft:oak_planks", "Oak Planks"),
            row("minecraft:iron_ingot", "Iron Ingot"),
            row("minecraft:cyan_wool", "Cyan Wool"),
        )

        assertEquals(3, html.split("class=\"item-glyph").size - 1, "one glyph per row")
        assertTrue(html.contains("<svg class=\"item-glyph"), "glyph is an inline svg, not an <img>")
    }

    /**
     * The tint is the whole reason the glyph set is 73 drawings rather than 1,540: shape is drawn
     * once, material is a CSS class. If the class stops being emitted every wool is grey.
     */
    @Test
    fun `the tint class comes from the item id`() {
        val html = render(
            row("minecraft:cyan_wool", "Cyan Wool"),
            row("minecraft:dark_oak_planks", "Dark Oak Planks"),
        )

        assertTrue(html.contains("item-glyph--cyan"), "cyan_wool should tint cyan")
        assertTrue(html.contains("item-glyph--dark-oak"), "dark_oak_planks should tint dark-oak")
    }

    /**
     * The fallback has to reach a real page for MCO-475's "every user is a detector" argument to
     * mean anything — a gap nobody can see is a gap nobody reports.
     */
    @Test
    fun `an id no rule covers renders the unmapped mark`() {
        val html = render(row("minecraft:seam_not_a_real_item", "Definitely Not An Item"))

        assertTrue(html.contains("item-glyph--unmapped"), "unmapped ids get the crossed-box mark")
    }

    @Test
    fun `the ignored section draws glyphs too`() {
        val html = render(
            row("minecraft:iron_ingot", "Iron Ingot"),
            row("minecraft:redstone", "Redstone", ignored = true),
        )

        assertTrue(html.contains("plan-ignored-row-"), "the ignored section rendered")
        // One for the active row, one for the ignored one.
        assertEquals(2, html.split("class=\"item-glyph").size - 1)
    }

    /**
     * 16px is what `itemGlyph`'s default emits and what the 40px row is built around. Pinned
     * because "does the drawing survive this size" was the open question on the surface, and a
     * silent change of the default would answer it differently without anyone looking.
     */
    @Test
    fun `rows use the 16px default`() {
        val html = render(row("minecraft:iron_ingot", "Iron Ingot"))

        assertTrue(html.contains("width=\"16px\" height=\"16px\""), "row glyphs are 16px")
    }
}
