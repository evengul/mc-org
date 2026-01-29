package app.mcorg.pipeline.minecraft.extract

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.extract.recipe.ExtractRecipesStep
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun JsonElement.objectResult(filename: String): Result<AppFailure, JsonObject> {
    return if (this is JsonObject) {
        Result.success(this)
    } else {
        Result.failure(AppFailure.FileError(ExtractRecipesStep.javaClass, filename))
    }
}

fun JsonObject.getResult(key: String, filename: String): Result<AppFailure, JsonElement> {
    return this[key]?.let {
        Result.success(it)
    } ?: run {
        Result.failure(AppFailure.FileError(ExtractRecipesStep.javaClass, filename))
    }
}

fun JsonElement.arrayResult(filename: String): Result<AppFailure, JsonArray> {
    return if (this is JsonArray) {
        Result.success(this)
    } else {
        Result.failure(AppFailure.FileError(ExtractRecipesStep.javaClass, filename))
    }
}

fun JsonElement.primitiveResult(filename: String): Result<AppFailure, JsonPrimitive> {
    return if (this is JsonPrimitive) {
        Result.success(this)
    } else {
        Result.failure(AppFailure.FileError(ExtractRecipesStep.javaClass, filename))
    }
}