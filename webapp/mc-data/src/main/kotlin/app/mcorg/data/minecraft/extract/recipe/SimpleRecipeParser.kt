package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.domain.model.minecraft.MinecraftId
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
        // A simple recipe (smelting, blasting, …) has a single ingredient slot. A list there is
        // a set of alternatives ("any of"), not several required inputs — collapse it to one
        // synthetic choice tag rather than requiring every alternative.
        val ingredient: Result<ExtractionFailure, MinecraftId> = json.objectResult(filename)
            .flatMap { it.getResult("ingredient", filename) }
            .flatMap { element ->
                when (element) {
                    is JsonArray -> {
                        val members = element.mapNotNull { value ->
                            parseItemRef(value).also {
                                if (it == null) logger.warn("Unknown ingredient value in simple recipe: $value in file $filename")
                            }
                        }
                        // A simple recipe must have an ingredient: an all-unresolvable list is a failure
                        // (not a silently dropped slot), unlike the optional slots in shaped/shapeless.
                        choiceFrom(members)?.let { Result.success(it) } ?: Result.failure(
                            ExtractionFailure.JsonFailure.UnknownValue(element.toString(), "ingredient", element, filename)
                        )
                    }
                    else -> requireItemRef(element, "ingredient", filename)
                        .mapSuccess { MinecraftIdFactory.minecraftIdFromId(it) }
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
                requiredItems = listOf(ingredient.getOrThrow() to RecipeQuantityParser.ingredientQuantity()),
                producedItems = listOf(
                    MinecraftIdFactory.minecraftIdFromId(result.getOrThrow()) to resultQuantity
                )
            )
        )
    }
}
