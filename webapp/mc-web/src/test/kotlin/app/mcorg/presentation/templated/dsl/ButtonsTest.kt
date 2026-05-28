package app.mcorg.presentation.templated.dsl

import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ButtonsTest {

    private fun render(block: kotlinx.html.FlowContent.() -> Unit): String {
        return createHTML().div { block() }
    }

    @Test
    fun `primaryButton emits btn and btn--primary classes`() {
        val html = render { primaryButton { +"Save" } }
        assertTrue(html.contains("btn"))
        assertTrue(html.contains("btn--primary"))
        assertTrue(html.contains("Save"))
        assertTrue(html.contains("<button"))
    }

    @Test
    fun `secondaryButton emits btn and btn--secondary classes`() {
        val html = render { secondaryButton { +"Add Resource" } }
        assertTrue(html.contains("btn"))
        assertTrue(html.contains("btn--secondary"))
        assertTrue(html.contains("Add Resource"))
    }

    @Test
    fun `ghostButton emits btn and btn--ghost classes`() {
        val html = render { ghostButton { +"Cancel" } }
        assertTrue(html.contains("btn"))
        assertTrue(html.contains("btn--ghost"))
        assertTrue(html.contains("Cancel"))
    }

    @Test
    fun `dangerButton emits btn and btn--danger classes`() {
        val html = render { dangerButton { +"Delete" } }
        assertTrue(html.contains("btn"))
        assertTrue(html.contains("btn--danger"))
        assertTrue(html.contains("Delete"))
    }

    @Test
    fun `small modifier adds btn--sm class`() {
        val html = render { primaryButton(small = true) { +"Import" } }
        assertTrue(html.contains("btn--sm"))
        assertTrue(html.contains("btn--primary"))
    }

    @Test
    fun `default button does not include btn--sm`() {
        val html = render { primaryButton { +"Save" } }
        assertTrue(!html.contains("btn--sm"))
    }
}
