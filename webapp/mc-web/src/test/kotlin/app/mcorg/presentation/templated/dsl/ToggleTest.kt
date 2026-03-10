package app.mcorg.presentation.templated.dsl

import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ToggleTest {

    private fun render(block: kotlinx.html.FlowContent.() -> Unit): String {
        return createHTML().div { block() }
    }

    @Test
    fun `toggle emits div with toggle class`() {
        val html = render { planExecuteToggle(active = "PLAN") }
        assertTrue(html.contains("class=\"toggle\""))
    }

    @Test
    fun `toggle has exactly two child buttons`() {
        val html = render { planExecuteToggle(active = "PLAN") }
        val buttonCount = Regex("<button").findAll(html).count()
        assertEquals(2, buttonCount)
    }

    @Test
    fun `PLAN active marks first button active`() {
        val html = render { planExecuteToggle(active = "PLAN") }
        assertTrue(html.contains("toggle__btn--active"))
        assertTrue(html.contains("PLAN"))
        assertTrue(html.contains("EXEC"))
        // The active class should appear before PLAN text
        val activeIndex = html.indexOf("toggle__btn--active")
        val planIndex = html.indexOf("PLAN")
        val execIndex = html.indexOf("EXEC")
        assertTrue(activeIndex < planIndex)
        // Second button should not have active class after EXEC
        val secondActiveIndex = html.indexOf("toggle__btn--active", planIndex)
        assertTrue(secondActiveIndex == -1 || secondActiveIndex > execIndex)
    }

    @Test
    fun `EXECUTE active marks second button active`() {
        val html = render { planExecuteToggle(active = "EXECUTE") }
        assertTrue(html.contains("toggle__btn--active"))
        // First button (PLAN) should NOT have active class
        val planIndex = html.indexOf("PLAN")
        val execIndex = html.indexOf("EXEC")
        val activeIndex = html.indexOf("toggle__btn--active")
        assertTrue(activeIndex > planIndex && activeIndex < execIndex)
    }
}
