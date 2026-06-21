package app.mcorg.event

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import app.mcorg.domain.model.project.ProjectType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit tests for the [EventEnvelope] wire contract (MCO-228). */
class EventEnvelopeTest {

    private val ts = Instant.parse("2026-06-21T12:00:00Z")

    @Test
    fun `envelope carries event_type, world_id, timestamp, actor and data`() {
        val json = EventEnvelope.serialize(
            ProjectCreated(worldId = 3, actorId = 42, timestamp = ts, projectId = 9, name = "Iron Farm", type = ProjectType.REDSTONE)
        )
        val obj = Json.parseToJsonElement(json).jsonObject

        assertEquals("project_created", obj["event_type"]!!.jsonPrimitive.content)
        assertEquals(3, obj["world_id"]!!.jsonPrimitive.content.toInt())
        assertEquals("2026-06-21T12:00:00Z", obj["timestamp"]!!.jsonPrimitive.content)
        assertEquals(42, obj["actor"]!!.jsonPrimitive.content.toInt())

        val data = (obj["data"] as JsonObject)
        assertEquals(9, data["project_id"]!!.jsonPrimitive.content.toInt())
        assertEquals("Iron Farm", data["name"]!!.jsonPrimitive.content)
        assertEquals("REDSTONE", data["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `system-originated event serializes actor as null`() {
        val json = EventEnvelope.serialize(
            IdeaImported(worldId = 1, actorId = null, timestamp = ts, ideaId = 5, name = "Auto-sorter")
        )
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals(JsonNull, obj["actor"])
    }
}
