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
        val html = render { planExecuteToggle(worldId = 1, active = "plan") }
        assertTrue(html.contains("class=\"toggle\""))
    }

    @Test
    fun `toggle has exactly two child buttons`() {
        val html = render { planExecuteToggle(worldId = 1, active = "plan") }
        val buttonCount = Regex("<button").findAll(html).count()
        assertEquals(2, buttonCount)
    }

    @Test
    fun `plan active marks PLAN button active`() {
        val html = render { planExecuteToggle(worldId = 1, active = "plan") }
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
    fun `execute active marks EXEC button active`() {
        val html = render { planExecuteToggle(worldId = 1, active = "execute") }
        assertTrue(html.contains("toggle__btn--active"))
        // First button (PLAN) should NOT have active class
        val planIndex = html.indexOf("PLAN")
        val execIndex = html.indexOf("EXEC")
        val activeIndex = html.indexOf("toggle__btn--active")
        assertTrue(activeIndex > planIndex && activeIndex < execIndex)
    }

    @Test
    fun `PLAN button emits correct hx-get attribute`() {
        val html = render { planExecuteToggle(worldId = 42, active = "plan") }
        assertTrue(html.contains("/worlds/42/projects/list-fragment?view=plan"))
    }

    @Test
    fun `EXEC button emits correct hx-get attribute`() {
        val html = render { planExecuteToggle(worldId = 42, active = "execute") }
        assertTrue(html.contains("/worlds/42/projects/list-fragment?view=execute"))
    }

    @Test
    fun `buttons emit hx-target for projects-view`() {
        val html = render { planExecuteToggle(worldId = 1, active = "plan") }
        assertTrue(html.contains("hx-target=\"#projects-view\""))
    }

    @Test
    fun `PLAN button emits correct hx-push-url`() {
        val html = render { planExecuteToggle(worldId = 42, active = "plan") }
        assertTrue(html.contains("/worlds/42/projects?view=plan"))
    }

    @Test
    fun `EXEC button emits correct hx-push-url`() {
        val html = render { planExecuteToggle(worldId = 42, active = "execute") }
        assertTrue(html.contains("/worlds/42/projects?view=execute"))
    }
}
