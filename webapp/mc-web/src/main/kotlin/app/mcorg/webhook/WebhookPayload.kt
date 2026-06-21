package app.mcorg.webhook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Builds the HTTP body for a batch of due deliveries to one subscription. A single event is sent
 * unwrapped (the bare envelope); multiple events accumulated within a poll window are wrapped as
 * `{ "events": [ envelope, envelope, … ] }`. Consumers must handle both shapes.
 */
object WebhookPayload {
    private val json = Json

    /** [envelopes] are already-serialized event envelopes (JSON strings); must be non-empty. */
    fun build(envelopes: List<String>): String {
        require(envelopes.isNotEmpty()) { "Cannot build a webhook payload from zero events" }
        if (envelopes.size == 1) return envelopes.single()
        val batch: JsonObject = buildJsonObject {
            put("events", JsonArray(envelopes.map { json.parseToJsonElement(it) }))
        }
        return json.encodeToString(JsonObject.serializer(), batch)
    }
}
