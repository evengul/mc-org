package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

object SimpleRecipeParser {
    private val logger = LoggerFactory.getLogger(SimpleRecipeParser::class.java)

    suspend fun parse(
        json: JsonElement,
        sourceType: ResourceSource.SourceType,
        filename: String
    ): Result<ExtractionFailure, ResourceSource> {
        val ingredient = json.objectResult(filename)
            .flatMap { it.getResult("ingredient", filename) }
            .flatMap { ingredient ->
                when (ingredient) {
                    is JsonArray -> Result.success(ingredient.mapNotNull { value ->
                        parseItemRef(value).also {
                            if (it == null) logger.warn("Unknown ingredient value in simple recipe: $value in file $filename")
                        }
                    })
                    else -> requireItemRef(ingredient, "ingredient", filename).mapSuccess { listOf(it) }
                }
            }

        val result = RecipeItemIdParser.parse(json, filename)

        if (ingredient is Result.Failure || result is Result.Failure) {
            logger.warn("Smelting recipe missing ingredient or result id in $filename")
            return Result.failure(
                ExtractionFailure.Multiple(
                    buildList {
                        if (ingredient is Result.Failure) add(ingredient.error)
                        if (result is Result.Failure) add(result.error)
                    }
                )
            )
        }

        val resultQuantity = RecipeQuantityParser.parseResultQuantity(json, filename)

        return Result.success(
            ResourceSource(
                type = sourceType,
                filename = filename,
                requiredItems = ingredient.getOrThrow().map { MinecraftIdFactory.minecraftIdFromId(it) to RecipeQuantityParser.ingredientQuantity() },
                producedItems = listOf(
                    MinecraftIdFactory.minecraftIdFromId(result.getOrThrow()) to resultQuantity
                )
            )
        )
    }
}
