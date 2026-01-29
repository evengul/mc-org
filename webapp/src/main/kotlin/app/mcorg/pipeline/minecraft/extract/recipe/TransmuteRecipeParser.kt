package app.mcorg.pipeline.minecraft.extract.recipe

import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.extract.getResult
import app.mcorg.pipeline.minecraft.extract.objectResult
import app.mcorg.pipeline.minecraft.extract.primitiveResult
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

object TransmuteRecipeParser {
    private val logger = LoggerFactory.getLogger(TransmuteRecipeParser::class.java)

    suspend fun parse(
        json: JsonElement,
        filename: String
    ) : Result<AppFailure, List<ResourceSource>> {
        val input = json.objectResult(filename).flatMap { it.getResult("input", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
        val material = json.objectResult(filename).flatMap { it.getResult("material", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
        val result =  RecipeItemIdParser.parse(json, filename)

        if (input is Result.Failure || material is Result.Failure || result is Result.Failure) {
            logger.warn("Transmute recipe missing input, material, or result id in $filename")
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
                    type = ResourceSource.SourceType.RecipeTypes.CRAFTING_TRANSMUTE,
                    filename = filename,
                    requiredItems = listOf(
                        MinecraftIdFactory.minecraftIdFromId(input.getOrThrow()),
                        MinecraftIdFactory.minecraftIdFromId(material.getOrThrow())
                    ), producedItems = listOf(
                        MinecraftIdFactory.minecraftIdFromId(result.getOrThrow())
                    )
                )
            )
        )
    }
}