package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.extract.primitiveResult
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

object SmithingTransformParser {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun parse(
        json: JsonElement,
        filename: String
    ) : Result<ExtractionFailure, ResourceSource> {
        val base = json.objectResult(filename).flatMap { it.getResult("base", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
            .recover { json.objectResult(filename).flatMap { it.getResult("base", filename) }.flatMap { it.objectResult(filename) }.flatMap { it.getResult("key", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } } }
            .recover { json.objectResult(filename).flatMap { it.getResult("base", filename) }.flatMap { it.objectResult(filename) }.flatMap { it.getResult("item", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } } }
        val addition = json.objectResult(filename).flatMap { it.getResult("addition", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
            .recover { json.objectResult(filename).flatMap { it.getResult("addition", filename) }.flatMap { it.objectResult(filename) }.flatMap { it.getResult("key", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } } }
            .recover { json.objectResult(filename).flatMap { it.getResult("addition", filename) }.flatMap { it.objectResult(filename) }.flatMap { it.getResult("item", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } } }
        val result =  RecipeItemIdParser.parse(json, filename)

        if (base is Result.Failure || addition is Result.Failure || result is Result.Failure) {
            logger.warn("Smithing transform recipe missing base, addition, or result id in $filename")
            return Result.failure(
                ExtractionFailure.Multiple(
                    buildList {
                        if (base is Result.Failure) add(base.error)
                        if (addition is Result.Failure) add(addition.error)
                    }
                )
            )
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
