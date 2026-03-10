package app.mcorg.presentation.templated.dsl

import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LayoutTest {

    private fun render(block: kotlinx.html.FlowContent.() -> Unit): String {
        return createHTML().div { block() }
    }

    @Test
    fun `pageShell returns complete HTML document`() {
        val html = pageShell(pageTitle = "Test Page") {
            div { +"Hello" }
        }
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<html"))
        assertTrue(html.contains("lang=\"en\""))
    }

    @Test
    fun `pageShell includes charset meta`() {
        val html = pageShell { div { } }
        assertTrue(html.contains("charset=\"utf-8\"") || html.contains("charset=\"UTF-8\""))
    }

    @Test
    fun `pageShell includes viewport meta`() {
        val html = pageShell { div { } }
        assertTrue(html.contains("viewport"))
        assertTrue(html.contains("width=device-width"))
    }

    @Test
    fun `pageShell includes page title`() {
        val html = pageShell(pageTitle = "Projects — Survival") { div { } }
        assertTrue(html.contains("<title>Projects — Survival</title>"))
    }

    @Test
    fun `pageShell includes self-hosted fonts via design-tokens css`() {
        val html = pageShell { div { } }
        assertTrue(html.contains("/static/styles/design-tokens.css"))
    }

    @Test
    fun `pageShell includes reset css`() {
        val html = pageShell { div { } }
        assertTrue(html.contains("/static/styles/reset.css"))
    }

    @Test
    fun `pageShell does not include Google Fonts`() {
        val html = pageShell { div { } }
        assertFalse(html.contains("fonts.googleapis.com"))
        assertFalse(html.contains("fonts.gstatic.com"))
    }

    @Test
    fun `pageShell does not include old CSS files`() {
        val html = pageShell { div { } }
        assertFalse(html.contains("/static/styles/root.css"))
        assertFalse(html.contains("/static/styles/styles.css"))
    }

    @Test
    fun `pageShell does not include theme script`() {
        val html = pageShell { div { } }
        assertFalse(html.contains("theme-switcher"))
        assertFalse(html.contains("data-theme"))
    }

    @Test
    fun `pageShell does not include confirmation modal script`() {
        val html = pageShell { div { } }
        assertFalse(html.contains("confirmation-modal"))
    }

    @Test
    fun `pageShell includes HTMX script`() {
        val html = pageShell { div { } }
        assertTrue(html.contains("htmx"))
    }

    @Test
    fun `pageShell includes response-targets script`() {
        val html = pageShell { div { } }
        assertTrue(html.contains("response-targets.js"))
    }

    @Test
    fun `pageShell sets hx-ext response-targets on body`() {
        val html = pageShell { div { } }
        assertTrue(html.contains("hx-ext=\"response-targets\""))
    }

    @Test
    fun `pageShell renders body content`() {
        val html = pageShell {
            div("my-content") { +"Hello World" }
        }
        assertTrue(html.contains("my-content"))
        assertTrue(html.contains("Hello World"))
    }

    @Test
    fun `container emits div with container class`() {
        val html = render { container { +"content" } }
        assertTrue(html.contains("class=\"container\""))
        assertTrue(html.contains("content"))
    }

    @Test
    fun `surface emits div with surface class`() {
        val html = render { surface { +"panel" } }
        assertTrue(html.contains("class=\"surface\""))
        assertTrue(html.contains("panel"))
    }

    @Test
    fun `divider emits div with divider class`() {
        val html = render { divider() }
        assertTrue(html.contains("class=\"divider\""))
    }
}
