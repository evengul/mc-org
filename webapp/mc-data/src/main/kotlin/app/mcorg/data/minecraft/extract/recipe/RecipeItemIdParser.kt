package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Resolves a recipe's result item id across the shapes used by different versions:
 * a top-level `id`, a primitive `result`, a `result` object carrying any [parseItemRef]
 * spelling, or 1.20.x's nested `result.value.id`.
 */
object RecipeItemIdParser {
    fun parse(json: JsonElement, filename: String): Result<ExtractionFailure, String> {
        val obj = json as? JsonObject
            ?: return Result.failure(ExtractionFailure.JsonFailure.NotAnObject(json, filename))

        (obj["id"] as? JsonPrimitive)?.contentOrNull?.let { return Result.success(it) }

        val result = obj["result"]
            ?: return Result.failure(ExtractionFailure.JsonFailure.KeyNotFound(obj, "result", filename))

        parseItemRef(result)?.let { return Result.success(it) }

        parseItemRef((result as? JsonObject)?.get("value"))?.let { return Result.success(it) }

        return Result.failure(ExtractionFailure.JsonFailure.KeyNotFound(result, "{id,item,key,value}", filename))
    }
}
