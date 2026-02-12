package app.mcorg.pipeline.minecraft.extract.recipe

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.extract.arrayResult
import app.mcorg.pipeline.minecraft.extract.getResult
import app.mcorg.pipeline.minecraft.extract.objectResult
import app.mcorg.pipeline.minecraft.extract.primitiveResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory

object CraftingValuesParser {
    private val logger = LoggerFactory.getLogger(CraftingValuesParser::class.java)

    suspend fun parse(
        json: JsonElement,
        filename: String
    ) : Result<AppFailure, Pair<String, List<List<String>>>> {
        val resultIdResult = RecipeItemIdParser.parse(json, filename)

        val ingredientListResult = json.objectResult(filename).flatMap { it.getResult("ingredients", filename) }
            .flatMap { it.arrayResult(filename) }
            .mapSuccess { ingredients ->
                ingredients.mapNotNull { value ->
                    runBlocking {
                        when (value) {
                            is JsonPrimitive -> value.contentOrNull?.let { listOf(it) }
                            is JsonArray -> value.mapNotNull { singleValue ->
                                when (singleValue) {
                                    is JsonPrimitive -> singleValue.contentOrNull
                                    is JsonObject -> singleValue.objectResult(filename)
                                        .flatMap { obj -> obj.getResult("item", filename) }
                                        .recover { singleValue.objectResult(filename).flatMap { i -> i.getResult("tag", filename).flatMap { tag -> tag.primitiveResult(filename) }.mapSuccess { tag -> JsonPrimitive("#${tag.content}") } } }
                                        .flatMap { it.primitiveResult(filename) }
                                        .mapSuccess { p -> p.content }
                                        .getOrNull()
                                    else -> {
                                        logger.warn("Unknown ingredient value type in array in recipe: ${singleValue.javaClass} in file $filename")
                                        null
                                    }
                                }
                            }
                            is JsonObject -> value.jsonObject.getResult("item", filename)
                                .recover { value.jsonObject.getResult("tag", filename).flatMap { tag -> tag.primitiveResult(filename) }.flatMap { tag -> Result.success(JsonPrimitive("#${tag.content}")) } }
                                .mapSuccess { it.primitiveResult(filename).mapSuccess { p -> p.content }.getOrNull()?.let { c -> listOf(c) } }.getOrNull()
                        }
                    }
                }
            }
            .recover {
                json.objectResult(filename)
                    .flatMap { it.getResult("key", filename) }
                    .flatMap { it.objectResult(filename) }
                    .mapSuccess { key ->
                        key.jsonObject.values.mapNotNull { singleKey ->
                            runBlocking {
                                when (singleKey) {
                                    is JsonPrimitive -> singleKey.contentOrNull?.let { keyValue -> listOf(keyValue) }
                                    is JsonArray -> singleKey.jsonArray.mapNotNull { innerSingleKey ->
                                        when (innerSingleKey) {
                                            is JsonPrimitive -> innerSingleKey.contentOrNull
                                            is JsonObject -> {
                                                val innerSingleKeyValue = innerSingleKey.jsonObject.getResult("key", filename).flatMap { item -> item.primitiveResult(filename).mapSuccess { p -> p.content } }
                                                    .recover { innerSingleKey.jsonObject.getResult("item", filename).flatMap { item -> item.primitiveResult(filename).mapSuccess { p -> p.content } } }
                                                    .recover { innerSingleKey.jsonObject.getResult("tag", filename).flatMap { item -> item.primitiveResult(filename).mapSuccess { p -> "#${p.content}" } } }
                                                if (innerSingleKeyValue is Result.Failure) {
                                                    logger.warn("Could not retrieve inner key from object in $filename")
                                                    null
                                                } else {
                                                    innerSingleKeyValue.getOrThrow()
                                                }
                                            }
                                            else -> {
                                                logger.warn("Unknown ingredient value type in inner key object in recipe: ${innerSingleKey.javaClass} in file $filename")
                                                null
                                            }
                                        }
                                    }
                                    is JsonObject -> {
                                        val innerKeyValue = singleKey.jsonObject.getResult("key", filename).flatMap { item -> item.primitiveResult(filename).mapSuccess { p -> listOf(p.content) } }
                                            .recover { singleKey.jsonObject.getResult("id", filename).flatMap { item -> item.primitiveResult(filename).mapSuccess { p -> listOf(p.content) } } }
                                            .recover { singleKey.jsonObject.getResult("item", filename).flatMap { item -> item.primitiveResult(filename).mapSuccess { p -> listOf(p.content) } } }
                                            .recover { singleKey.jsonObject.getResult("tag", filename).flatMap { item -> item.primitiveResult(filename).mapSuccess { p -> listOf("#${p.content}") } } }
                                        if (innerKeyValue is Result.Failure) {
                                            logger.warn("Could not retrieve key from object in $filename")
                                            null
                                        } else {
                                            innerKeyValue.getOrThrow()
                                        }
                                    }
                                }
                            }
                        }
                    }
            }

        if (resultIdResult is Result.Failure || ingredientListResult is Result.Failure) {
            logger.warn("Crafting recipe missing ingredients or result id in $filename")
            return Result.failure(AppFailure.FileError(ExtractRecipesStep.javaClass, filename))
        }

        return Result.success(
            resultIdResult.getOrThrow() to ingredientListResult.getOrThrow()
        )
    }
}