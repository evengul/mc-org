package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.extract.primitiveResult
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

object TransmuteRecipeParser {
    private val logger = LoggerFactory.getLogger(TransmuteRecipeParser::class.java)

    suspend fun parse(
        json: JsonElement,
        filename: String
    ) : Result<ExtractionFailure, ResourceSource> {
        val input = json.objectResult(filename).flatMap { it.getResult("input", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
        val material = json.objectResult(filename).flatMap { it.getResult("material", filename) }.flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }

        // A transmute keeps the input item and only rewrites its components, so an *empty*
        // result means "the input, unchanged in id". 26.3 started leaning on that default:
        // `map_cloning.json` writes `"result": {}` where 26.2 spelled out
        // `{"id": "minecraft:filled_map"}` (MCO-567). Falling back to the input reproduces the
        // older versions' extracted source exactly.
        //
        // Deliberately narrow: only an empty result object falls back, and only once the input
        // itself parsed. A recipe with no `result` key at all is still a failure — that is a
        // shape nothing in Mojang's data writes, and the kind of thing extraction is fail-fast
        // in order to notice.
        val hasEmptyResult = (json as? JsonObject)?.get("result").let { it is JsonObject && it.isEmpty() }
        val result = RecipeItemIdParser.parse(json, filename).recover { failure ->
            if (hasEmptyResult && input is Result.Success) Result.success(input.value) else Result.failure(failure)
        }

        if (input is Result.Failure || material is Result.Failure || result is Result.Failure) {
            logger.warn("Transmute recipe missing input, material, or result id in $filename")
            return Result.failure(ExtractionFailure.Multiple(
                buildList {
                    if (input is Result.Failure) add(input.error)
                    if (material is Result.Failure) add(material.error)
                    if (result is Result.Failure) add(result.error)
                }
            ))
        }

        val resultQuantity = RecipeQuantityParser.parseResultQuantity(json, filename)

        return Result.success(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_TRANSMUTE,
                filename = filename,
                requiredItems = listOf(
                    MinecraftIdFactory.minecraftIdFromId(input.getOrThrow()) to RecipeQuantityParser.ingredientQuantity(),
                    MinecraftIdFactory.minecraftIdFromId(material.getOrThrow()) to RecipeQuantityParser.ingredientQuantity()
                ), producedItems = listOf(
                    MinecraftIdFactory.minecraftIdFromId(result.getOrThrow()) to resultQuantity
                )
            )
        )
    }
}
