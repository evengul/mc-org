package app.mcorg.presentation.templated.dsl

import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The world tabs (MCO-474), which replaced the PLAN/EXEC toggle. Roadmap and, for an admin,
 * Settings: the Projects tab between them was removed by MCO-692.
 */
class WorldTabsTest {

    private fun render(block: FlowContent.() -> Unit): String = createHTML().div { block() }

    @Test
    fun `there is no Projects tab`() {
        val html = render { worldTabs(worldId = 7, active = WorldTab.ROADMAP, isWorldAdmin = true) }

        assertContains(html, "/worlds/7/roadmap")
        assertFalse(html.contains("/worlds/7/projects"), "the roadmap carries what the tab had")
        assertFalse(html.contains(">Projects<"))
    }

    @Test
    fun `marks the active tab and only the active tab`() {
        val html = render { worldTabs(worldId = 1, active = WorldTab.ROADMAP, isWorldAdmin = true) }

        assertEquals(1, Regex("world-tabs__tab--active").findAll(html).count())
        assertEquals(1, Regex("aria-current=\"page\"").findAll(html).count())
    }

    @Test
    fun `the active marker follows the argument`() {
        val roadmap = render { worldTabs(worldId = 1, active = WorldTab.ROADMAP, isWorldAdmin = true) }
        val settings = render { worldTabs(worldId = 1, active = WorldTab.SETTINGS, isWorldAdmin = true) }

        // The active class sits on the anchor, so the section it precedes is the marked one.
        assertTrue(roadmap.indexOf("--active") < roadmap.indexOf("Settings"))
        assertTrue(settings.indexOf("--active") > settings.indexOf("Roadmap"))
    }

    /**
     * The primary user is red-green colour-blind, so an active tab must never be signalled by
     * colour alone. The class and `aria-current` are what carry it; this pins that they are
     * both present rather than one being dropped as redundant.
     */
    @Test
    fun `active state is carried by markup, not only by styling`() {
        val html = render { worldTabs(worldId = 3, active = WorldTab.ROADMAP) }

        assertContains(html, "world-tabs__tab--active")
        assertContains(html, "aria-current=\"page\"")
    }

    @Test
    fun `an admin gets a Settings tab, labelled in words`() {
        val html = render { worldTabs(worldId = 5, active = WorldTab.ROADMAP, isWorldAdmin = true) }

        assertContains(html, "/worlds/5/settings")
        assertContains(html, "Settings")
    }

    /** The settings route is admin-gated, and a tab that answers 403 is worse than no tab. */
    @Test
    fun `a member gets no Settings tab`() {
        val html = render { worldTabs(worldId = 5, active = WorldTab.ROADMAP) }

        assertFalse(html.contains("/worlds/5/settings"))
    }

    @Test
    fun `world id is threaded into every link`() {
        val html = render { worldTabs(worldId = 42, active = WorldTab.ROADMAP, isWorldAdmin = true) }

        assertContains(html, "/worlds/42/roadmap")
        assertContains(html, "/worlds/42/settings")
        assertFalse(html.contains("/worlds/1/"), "no other world should be linked")
    }
}
