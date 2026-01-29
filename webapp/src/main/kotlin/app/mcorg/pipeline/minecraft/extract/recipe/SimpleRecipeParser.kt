package app.mcorg.pipeline.minecraft.extract.recipe

import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.extract.getResult
import app.mcorg.pipeline.minecraft.extract.objectResult
import app.mcorg.pipeline.minecraft.extract.primitiveResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

object SimpleRecipeParser {
    private val logger = LoggerFactory.getLogger(SimpleRecipeParser::class.java)

    suspend fun parse(
        json: JsonElement,
        sourceType: ResourceSource.SourceType,
        filename: String
    ): Result<AppFailure, List<ResourceSource>> {
        val ingredient = json.objectResult(filename)
            .flatMap { it.getResult("ingredient", filename) }
            .flatMap { ingredient ->
                when (ingredient) {
                    is JsonPrimitive -> Result.success(ingredient.jsonPrimitive.content.let { listOf(it) })
                    is JsonArray -> Result.success(ingredient.jsonArray.mapNotNull { value ->
                        when (value) {
                            is JsonPrimitive -> value.contentOrNull
                            is JsonObject -> value.objectResult(filename)
                                .flatMap { it.getResult("key", filename) }
                                .recover { value.objectResult(filename).flatMap { i -> i.getResult("tag", filename).flatMap { tag -> tag.primitiveResult(filename) }.mapSuccess { tag -> JsonPrimitive("#${tag.content}") } } }
                                .flatMap { it.primitiveResult(filename) }
                                .mapSuccess { p -> p.content }
                                .getOrNull()
                            else -> {
                                logger.warn("Unknown ingredient value type in simple recipe: ${value.javaClass} in file $filename")
                                null
                            }
                        }
                    })
                    is JsonObject -> ingredient.objectResult(filename)
                        .flatMap { it.getResult("value", filename)
                            .recover { ingredient.objectResult(filename).flatMap { i -> i.getResult("id", filename) } }
                            .recover { ingredient.objectResult(filename).flatMap { i -> i.getResult("key", filename) } }
                            .recover { ingredient.objectResult(filename).flatMap { i -> i.getResult("item", filename) } }
                            .recover { ingredient.objectResult(filename).flatMap { i -> i.getResult("tag", filename).flatMap { tag -> tag.primitiveResult(filename) }.mapSuccess { tag -> JsonPrimitive("#${tag.content}") } } }
                        }
                        .flatMap { it.primitiveResult(filename).mapSuccess { p -> listOf(p.content) } }
                }
            }

        val result = RecipeItemIdParser.parse(json, filename)

        if (ingredient is Result.Failure || result is Result.Failure) {
            logger.warn("Smelting recipe missing ingredient or result id in $filename")
            return Result.success(
                listOf(
                    ResourceSource(
                        type = ResourceSource.SourceType.UNKNOWN,
                        filename = filename
                    )
                )
            )
        }

        return Result.success(
            listOf(
                ResourceSource(
                    type = sourceType,
                    filename = filename,
                    requiredItems = ingredient.getOrThrow().map { MinecraftIdFactory.minecraftIdFromId(it) },
                    producedItems = listOf(
                        MinecraftIdFactory.minecraftIdFromId(result.getOrThrow())
                    )
                )
            )
        )
    }
}