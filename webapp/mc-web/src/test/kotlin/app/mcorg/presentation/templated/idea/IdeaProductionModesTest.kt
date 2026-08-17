package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.IdeaProductionMode
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MCO-412 — what an idea produces, on its detail page.
 *
 * Replaces the `productionRate` category-data rendering covered by IdeaDetailFieldsTest, which
 * went when production stopped being a FARM schema field.
 */
class IdeaProductionModesTest {

    private fun render(modes: List<IdeaProductionMode>) = createHTML().div { ideaProductionModes(modes) }

    private fun mode(name: String, position: Int, vararg rates: Pair<String, Int?>) =
        IdeaProductionMode(id = position + 1, name = name, position = position, rates = rates.toMap())

    @Test
    fun `a single mode shows its rates without ever saying "mode"`() {
        // The author was never asked about modes, so naming the implicit one back at them would
        // attribute a choice they did not make.
        val html = render(listOf(mode(IdeaProductionMode.DEFAULT_MODE_NAME, 0, "minecraft:ice" to 71_000)))

        assertContains(html, "71${DIGIT_GROUP_SEPARATOR}000")
        assertContains(html, "Ice")
        assertFalse(html.contains(IdeaProductionMode.DEFAULT_MODE_NAME))
    }

    @Test
    fun `several modes are named, because now there is something to tell apart`() {
        val html = render(
            listOf(
                mode("Max speed", 0, "minecraft:ice" to 62_000),
                mode("Slowed", 1, "minecraft:ice" to 18_000),
            )
        )

        assertContains(html, "Max speed")
        assertContains(html, "Slowed")
        assertContains(html, "62${DIGIT_GROUP_SEPARATOR}000")
        assertContains(html, "18${DIGIT_GROUP_SEPARATOR}000")
    }

    @Test
    fun `a named mode is a heading, not a run of styled text`() {
        // MCO-419: two modes are two sub-sections of "Produces", and a screen reader jumping by
        // heading should land on each one.
        val html = render(
            listOf(
                mode("Max speed", 0, "minecraft:ice" to 62_000),
                mode("Slowed", 1, "minecraft:ice" to 18_000),
            )
        )

        assertContains(html, "<h3 class=\"idea-productions__mode-name\">Max speed</h3>")
    }

    @Test
    fun `item ids are tidied for reading`() {
        val html = render(listOf(mode("Default", 0, "minecraft:wither_skeleton_skull" to 40)))

        assertContains(html, "Wither Skeleton Skull")
        assertFalse(html.contains("minecraft:"))
    }

    @Test
    fun `rates within a mode read largest first`() {
        val html = render(
            listOf(mode("Everything", 0, "minecraft:blaze_rod" to 500, "minecraft:bone" to 900))
        )

        assertTrue(html.indexOf("900") < html.indexOf("500"))
    }

    @Test
    fun `an unmeasured item keeps its row and is marked as such`() {
        // MCO-419: the row carries a modifier class so "has a number" and "has no number" are
        // distinguishable by sight, not only by reading the words.
        val html = render(listOf(mode("Default", 0, "minecraft:bamboo" to null)))

        assertContains(html, "idea-productions__rate--unmeasured")
        assertContains(html, "Bamboo")
        assertContains(html, "rate unmeasured")
    }

    @Test
    fun `an idea that produces nothing renders no section at all`() {
        // Most builds produce nothing. An empty "Produces" heading would read as missing data.
        assertFalse(render(emptyList()).contains("Produces"))
        assertFalse(render(listOf(mode("Default", 0))).contains("Produces"))
    }
}
