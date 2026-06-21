package app.mcorg.webhook

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebhookFilterTest {

    @Test
    fun `wildcard filter matches any event type`() {
        assertTrue(eventMatchesFilter(listOf("*"), "project_created"))
        assertTrue(eventMatchesFilter(listOf("*"), "anything_at_all"))
    }

    @Test
    fun `explicit filter matches only listed event types`() {
        val filter = listOf("project_created", "idea_imported")
        assertTrue(eventMatchesFilter(filter, "project_created"))
        assertTrue(eventMatchesFilter(filter, "idea_imported"))
        assertFalse(eventMatchesFilter(filter, "task_toggled"))
    }

    @Test
    fun `empty filter matches nothing`() {
        assertFalse(eventMatchesFilter(emptyList(), "project_created"))
    }
}
