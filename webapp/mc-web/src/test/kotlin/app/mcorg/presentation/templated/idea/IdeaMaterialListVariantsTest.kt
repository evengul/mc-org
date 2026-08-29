package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.IdeaModeKind
import app.mcorg.domain.model.idea.IdeaProductionMode
import app.mcorg.pipeline.idea.single.IdeaMaterial
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * MCO-463 — a design whose materials live on its build-time variants still shows them.
 *
 * Found in a browser, not here: the first design ever saved with a variant rendered with **no
 * materials section at all**. Its fifteen materials were real and stored, but they hang off the
 * variant (`mode_id`), and the page's query had just been narrowed to the base list — correctly, or
 * a four-variant farm would read as the sum of all four.
 */
class IdeaMaterialListVariantsTest {

    private fun render(materials: List<IdeaMaterial>, modes: List<IdeaProductionMode>) =
        createHTML().div { ideaMaterialList(materials, modes) }

    private fun variant(name: String, position: Int, requirements: Map<String, Int>) = IdeaProductionMode(
        id = position + 1,
        name = name,
        position = position,
        rates = emptyMap(),
        kind = IdeaModeKind.BUILD_TIME,
        requirements = requirements,
    )

    private val cobbleVariants = listOf(
        variant("1 module", 0, mapOf("minecraft:cobblestone" to 400)),
        variant("4 modules", 1, mapOf("minecraft:cobblestone" to 1_600, "minecraft:hopper" to 256)),
    )

    @Test
    fun `a design with one list renders exactly as it always did`() {
        val html = render(listOf(IdeaMaterial("minecraft:cobblestone", "Cobblestone", 400)), emptyList())

        assertContains(html, "Materials")
        assertContains(html, "Cobblestone")
        assertFalse(html.contains("idea-materials__variant"), "no variant chrome for a plain design")
    }

    @Test
    fun `a design whose materials live on its variants still shows them`() {
        val html = render(emptyList(), cobbleVariants)

        assertContains(html, "Materials")
        assertContains(html, "1 module")
        assertContains(html, "4 modules")
        assertContains(html, "minecraft:cobblestone".let { "Cobblestone" })
    }

    @Test
    fun `each variant carries its own total, which is the comparison worth showing`() {
        val html = render(emptyList(), cobbleVariants)

        // 400 against 1,856 (1,600 cobblestone + 256 hoppers) — the reason the variants exist.
        assertContains(html, "1 item, 400 total")
        assertContains(html, "2 items, 1${DIGIT_GROUP_SEPARATOR}856 total")
    }

    @Test
    fun `one variant is not described as a choice`() {
        // Caught in a browser: this read "This design can be built 1 ways" — ungrammatical, and
        // claiming a choice that does not exist.
        val html = render(emptyList(), listOf(cobbleVariants.first()))

        assertContains(html, "1 module")
        assertFalse(html.contains("1 ways"), "one variant is not a choice between ways")
    }

    @Test
    fun `a runtime mode contributes no material list`() {
        // Only a build-time mode changes what the build costs; a runtime one never owns materials.
        val runtime = listOf(IdeaProductionMode(1, "Max speed", 0, mapOf("minecraft:ice" to 71_000)))

        assertFalse(render(emptyList(), runtime).contains("Materials"))
    }

    @Test
    fun `a design with nothing at all renders nothing`() {
        assertFalse(render(emptyList(), emptyList()).contains("Materials"))
    }

    @Test
    fun `the base list wins when both somehow exist`() {
        // Should not arise — the write paths make them alternatives — but showing both would double
        // every quantity, which is the worst of the possible readings.
        val html = render(listOf(IdeaMaterial("minecraft:stone", "Stone", 12)), cobbleVariants)

        assertContains(html, "Stone")
        assertFalse(html.contains("4 modules"), "the base list is the whole answer when present")
    }
}
