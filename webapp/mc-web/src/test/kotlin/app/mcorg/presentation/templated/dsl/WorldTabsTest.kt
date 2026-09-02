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
 * The world tab pair (MCO-474), which replaced the PLAN/EXEC toggle as the way between a
 * world's roadmap and its project list.
 */
class WorldTabsTest {

    private fun render(block: FlowContent.() -> Unit): String = createHTML().div { block() }

    @Test
    fun `renders both sections, whichever one is active`() {
        val html = render { worldTabs(worldId = 7, active = WorldTab.ROADMAP) }

        assertContains(html, "/worlds/7/roadmap")
        assertContains(html, "/worlds/7/projects")
        assertContains(html, "Roadmap")
        assertContains(html, "Projects")
    }

    @Test
    fun `marks the active tab and only the active tab`() {
        val html = render { worldTabs(worldId = 1, active = WorldTab.ROADMAP) }

        assertEquals(1, Regex("world-tabs__tab--active").findAll(html).count())
        assertEquals(1, Regex("aria-current=\"page\"").findAll(html).count())
    }

    @Test
    fun `the active marker follows the argument`() {
        val roadmap = render { worldTabs(worldId = 1, active = WorldTab.ROADMAP) }
        val projects = render { worldTabs(worldId = 1, active = WorldTab.PROJECTS) }

        // The active class sits on the anchor, so the section it precedes is the marked one.
        assertTrue(roadmap.indexOf("--active") < roadmap.indexOf("Projects"))
        assertTrue(projects.indexOf("--active") > projects.indexOf("Roadmap"))
    }

    /**
     * The primary user is red-green colour-blind, so an active tab must never be signalled by
     * colour alone. The class and `aria-current` are what carry it; this pins that they are
     * both present rather than one being dropped as redundant.
     */
    @Test
    fun `active state is carried by markup, not only by styling`() {
        val html = render { worldTabs(worldId = 3, active = WorldTab.PROJECTS) }

        assertContains(html, "world-tabs__tab--active")
        assertContains(html, "aria-current=\"page\"")
    }

    @Test
    fun `world id is threaded into every link`() {
        val html = render { worldTabs(worldId = 42, active = WorldTab.ROADMAP) }

        assertContains(html, "/worlds/42/roadmap")
        assertContains(html, "/worlds/42/projects")
        assertFalse(html.contains("/worlds/1/"), "no other world should be linked")
    }
}
