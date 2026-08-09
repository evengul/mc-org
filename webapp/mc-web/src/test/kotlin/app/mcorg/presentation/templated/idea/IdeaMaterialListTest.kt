package app.mcorg.presentation.templated.idea

import app.mcorg.pipeline.idea.single.IdeaMaterial
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdeaMaterialListTest {

    private fun render(block: FlowContent.() -> Unit): String = createHTML().div { block() }

    @Test
    fun `renders nothing when an idea has no material list`() {
        val html = render { ideaMaterialList(emptyList()) }

        assertFalse(html.contains("Materials"))
    }

    @Test
    fun `drops the catalog's block and item disambiguators`() {
        // Extraction appends these to tell `block.minecraft.X` and `item.minecraft.X` apart. It is
        // an internal detail, not something a builder reading a shopping list should see.
        assertEquals("Oak Log", IdeaMaterial("minecraft:oak_log", "Oak Log (Block)", 1).displayName())
        assertEquals("Iron Ingot", IdeaMaterial("minecraft:iron_ingot", "Iron Ingot (Item)", 1).displayName())
    }

    @Test
    fun `falls back to a tidied id when the item is not in the catalog`() {
        assertEquals("Iron Ingot", IdeaMaterial("minecraft:iron_ingot", null, 1).displayName())
    }

    @Test
    fun `keeps the catalog name over the raw id where they differ`() {
        // Tidying the id alone would render "Tnt".
        assertEquals("TNT", IdeaMaterial("minecraft:tnt", "TNT (Block)", 1).displayName())
    }

    @Test
    fun `groups long counts so they can be read`() {
        assertEquals("9\u202F389\u202F854", 9_389_854L.formatWithSeparators())
        assertEquals("64", 64L.formatWithSeparators())
        assertEquals("1\u202F000", 1_000L.formatWithSeparators())
    }

    @Test
    fun `summarises the list and renders each row`() {
        val html = render {
            ideaMaterialList(
                listOf(
                    IdeaMaterial("minecraft:stone", "Stone (Block)", 1_728),
                    IdeaMaterial("minecraft:iron_ingot", "Iron Ingot (Item)", 64),
                )
            )
        }

        assertTrue(html.contains("Materials"))
        assertTrue(html.contains("2 items"))
        assertTrue(html.contains("1\u202F792 total"))
        assertTrue(html.contains("Stone"))
        assertTrue(html.contains("1\u202F728"))
        assertFalse(html.contains("(Block)"))
    }

    @Test
    fun `says item rather than items for a single entry`() {
        val html = render { ideaMaterialList(listOf(IdeaMaterial("minecraft:stone", "Stone (Block)", 3))) }

        assertTrue(html.contains("1 item,"))
    }
}
