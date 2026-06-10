package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.pipeline.Result
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Extracts an item reference from a recipe ingredient or result element. Recipe JSON has
 * spelled this differently across Minecraft versions: a plain string id, `{"item": id}`,
 * `{"id": id}`, `{"key": id}`, `{"value": id}`, or `{"tag": name}` (returned with the `#`
 * tag prefix). Returns null when the element holds no recognizable reference.
 */
internal fun parseItemRef(element: JsonElement?): String? = when (element) {
    null -> null
    is JsonPrimitive -> element.contentOrNull
    is JsonObject -> (element["tag"] as? JsonPrimitive)?.contentOrNull?.let { "#$it" }
        ?: sequenceOf("item", "id", "key", "value")
            .mapNotNull { key -> (element[key] as? JsonPrimitive)?.contentOrNull }
            .firstOrNull()
    else -> null
}

/** [parseItemRef] as a [Result], failing with the JSON key being parsed for error context. */
internal fun requireItemRef(element: JsonElement, key: String, filename: String): Result<ExtractionFailure, String> =
    parseItemRef(element)?.let { Result.success(it) }
        ?: Result.failure(ExtractionFailure.JsonFailure.UnknownValue(element.toString(), key, element, filename))
