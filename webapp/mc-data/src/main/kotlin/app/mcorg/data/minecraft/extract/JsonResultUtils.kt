package app.mcorg.data.minecraft.extract

import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.pipeline.Result
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun JsonElement.objectResult(filename: String): Result<ExtractionFailure, JsonObject> {
    return if (this is JsonObject) {
        Result.success(this)
    } else {
        Result.failure(ExtractionFailure.JsonFailure.NotAnObject(this, filename))
    }
}

fun JsonObject.getResult(key: String, filename: String): Result<ExtractionFailure, JsonElement> {
    return this[key]?.let {
        Result.success(it)
    } ?: run {
        Result.failure(ExtractionFailure.JsonFailure.KeyNotFound(this, key, filename))
    }
}

fun JsonElement.arrayResult(filename: String): Result<ExtractionFailure, JsonArray> {
    return if (this is JsonArray) {
        Result.success(this)
    } else {
        Result.failure(ExtractionFailure.JsonFailure.NotAnArray(this, filename))
    }
}

fun JsonElement.primitiveResult(filename: String): Result<ExtractionFailure, JsonPrimitive> {
    return if (this is JsonPrimitive) {
        Result.success(this)
    } else {
        Result.failure(ExtractionFailure.JsonFailure.NotAPrimitive(this, filename))
    }
}
