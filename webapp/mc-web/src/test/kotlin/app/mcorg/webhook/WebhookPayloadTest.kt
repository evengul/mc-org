package app.mcorg.webhook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WebhookPayloadTest {

    @Test
    fun `a single event is delivered unwrapped`() {
        val envelope = """{"event_type":"project_created","world_id":1}"""
        assertEquals(envelope, WebhookPayload.build(listOf(envelope)))
    }

    @Test
    fun `multiple events are wrapped in an events array preserving order`() {
        val first = """{"event_type":"project_created","world_id":1}"""
        val second = """{"event_type":"task_toggled","world_id":1}"""

        val body = WebhookPayload.build(listOf(first, second))
        val events = Json.parseToJsonElement(body).jsonObject["events"]!!.jsonArray

        assertEquals(2, events.size)
        assertEquals("project_created", events[0].jsonObject["event_type"]!!.jsonPrimitive.content)
        assertEquals("task_toggled", events[1].jsonObject["event_type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `building from zero events is rejected`() {
        assertFailsWith<IllegalArgumentException> { WebhookPayload.build(emptyList()) }
    }
}
