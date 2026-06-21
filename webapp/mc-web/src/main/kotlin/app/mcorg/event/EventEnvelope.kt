package app.mcorg.event

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.format.DateTimeFormatter

/**
 * Serializes a [SeamEvent] into the wire envelope shared with downstream consumers (webhook
 * delivery — MCO-229):
 *
 * ```json
 * { "event_type": "...", "world_id": 1, "timestamp": "ISO-8601", "actor": 42|null, "data": { ... } }
 * ```
 *
 * `actor` is `null` for system-originated events. `data` is the event-specific payload from
 * [SeamEvent.data]. Envelope versioning is deferred until the first schema change.
 */
object EventEnvelope {
    private val json = Json

    /** Build the envelope as a [JsonObject] (without serializing to a string). */
    fun toJsonObject(event: SeamEvent): JsonObject = buildJsonObject {
        put("event_type", event.eventType)
        put("world_id", event.worldId)
        put("timestamp", DateTimeFormatter.ISO_INSTANT.format(event.timestamp))
        put("actor", event.actorId)
        put("data", event.data())
    }

    /** Serialize the envelope to a compact JSON string. */
    fun serialize(event: SeamEvent): String =
        json.encodeToString(JsonObject.serializer(), toJsonObject(event))
}
