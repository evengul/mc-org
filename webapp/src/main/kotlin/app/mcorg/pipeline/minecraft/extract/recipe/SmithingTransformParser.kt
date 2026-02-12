package app.mcorg.pipeline.minecraft.extract.recipe

import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.extract.getResult
import app.mcorg.pipeline.minecraft.extract.objectResult
import app.mcorg.pipeline.minecraft.extract.primitiveResult
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

object SmithingTransformParser {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun parse(
        json: JsonElement,
        filename: String
    ) : Result<AppFailure, ResourceSource> {
        val base = json.objectResult(filename).flatMap { it.getResult("base", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
            .recover { json.objectResult(filename).flatMap { it.getResult("base", filename) }.flatMap { it.objectResult(filename) }.flatMap { it.getResult("key", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } } }
            .recover { json.objectResult(filename).flatMap { it.getResult("base", filename) }.flatMap { it.objectResult(filename) }.flatMap { it.getResult("item", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } } }
        val addition = json.objectResult(filename).flatMap { it.getResult("addition", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
            .recover { json.objectResult(filename).flatMap { it.getResult("addition", filename) }.flatMap { it.objectResult(filename) }.flatMap { it.getResult("key", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } } }
            .recover { json.objectResult(filename).flatMap { it.getResult("addition", filename) }.flatMap { it.objectResult(filename) }.flatMap { it.getResult("item", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } } }
        val result =  RecipeItemIdParser.parse(json, filename)

        if (base is Result.Failure || addition is Result.Failure || result is Result.Failure) {
            logger.warn("Smithing transform recipe missing base, addition, or result id in $filename")
            return Result.failure(AppFailure.FileError(ExtractRecipesStep.javaClass, filename))
        }

        val resultQuantity = RecipeQuantityParser.parseResultQuantity(json, filename)

        return Result.success(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.SMITHING_TRANSFORM,
                filename = filename,
                requiredItems = listOf(
                    MinecraftIdFactory.minecraftIdFromId(base.getOrThrow()) to RecipeQuantityParser.ingredientQuantity(),
                    MinecraftIdFactory.minecraftIdFromId(addition.getOrThrow()) to RecipeQuantityParser.ingredientQuantity()
                ),
                producedItems = listOf(
                    MinecraftIdFactory.minecraftIdFromId(result.getOrThrow()) to resultQuantity
                )
            )
        )
    }
}