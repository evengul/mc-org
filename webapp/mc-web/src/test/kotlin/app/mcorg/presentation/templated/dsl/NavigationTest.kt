package app.mcorg.presentation.templated.dsl

import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class NavigationTest {

    private fun render(block: kotlinx.html.FlowContent.() -> Unit): String {
        return createHTML().div { block() }
    }

    @Test
    fun `appHeader emits header with app-header class`() {
        val html = render { appHeader() }
        assertTrue(html.contains("<header"))
        assertTrue(html.contains("app-header"))
    }

    @Test
    fun `appHeader renders desktop and mobile sections`() {
        val html = render { appHeader(worldName = "Survival") }
        assertTrue(html.contains("app-header__desktop"))
        assertTrue(html.contains("app-header__mobile"))
    }

    @Test
    fun `appHeader renders logo`() {
        val html = render { appHeader() }
        assertTrue(html.contains("app-header__logo"))
        assertTrue(html.contains("MC-ORG"))
    }

    @Test
    fun `appHeader renders breadcrumb with single segment`() {
        val html = render {
            appHeader {
                breadcrumb("Worlds", "/worlds")
            }
        }
        assertTrue(html.contains("breadcrumb"))
        assertTrue(html.contains("Worlds"))
        assertTrue(html.contains("/worlds"))
    }

    @Test
    fun `appHeader renders breadcrumb with chained segments and current`() {
        val html = render {
            appHeader(worldName = "Survival") {
                breadcrumb("Worlds", "/worlds")
                    .link("Survival", "/worlds/1/projects")
                    .current("Iron Farm")
            }
        }
        assertTrue(html.contains("Worlds"))
        assertTrue(html.contains("Survival"))
        assertTrue(html.contains("Iron Farm"))
        assertTrue(html.contains("breadcrumb__item--current"))
        // Separators between segments
        val sepCount = Regex("breadcrumb__sep").findAll(html).count()
        assertEquals(2, sepCount)
    }

    @Test
    fun `appHeader renders Ideas link and settings`() {
        val html = render { appHeader() }
        assertTrue(html.contains("app-header__actions"))
        assertTrue(html.contains("Ideas"))
        assertTrue(html.contains("/ideas"))
    }

    @Test
    fun `mobile header shows world name`() {
        val html = render { appHeader(worldName = "Creative") }
        assertTrue(html.contains("app-header__world-name"))
        assertTrue(html.contains("Creative"))
    }

    @Test
    fun `mobile header shows MC-ORG when no world name`() {
        val html = render { appHeader() }
        assertTrue(html.contains("app-header__world-name"))
        // The mobile section should contain MC-ORG as fallback
        val mobileStart = html.indexOf("app-header__mobile")
        val mobileSection = html.substring(mobileStart)
        assertTrue(mobileSection.contains("MC-ORG"))
    }

    @Test
    fun `mobile header has hamburger button`() {
        val html = render { appHeader() }
        assertTrue(html.contains("app-header__hamburger"))
        assertTrue(html.contains("aria-label=\"Menu\""))
    }
}
