package app.mcorg.pipeline.minecraft.extract.recipe

import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

object ShapelessRecipeParser {
    private val logger = LoggerFactory.getLogger(ShapelessRecipeParser::class.java)

    suspend fun parse(
        json: JsonElement,
        filename: String
    ): Result<AppFailure, List<ResourceSource>> {
        val craftingValues = CraftingValuesParser.parse(json, filename)

        if (craftingValues is Result.Failure) {
            logger.warn("Shapeless recipe missing ingredients or result id in $filename")
            return Result.success(
                listOf(
                    ResourceSource(
                        type = ResourceSource.SourceType.UNKNOWN,
                        filename = filename
                    )
                )
            )
        }

        val (resultId, ingredientList) = craftingValues.getOrThrow()

        val combinations = ingredientList.fold(listOf(listOf<String>())) { acc, list ->
            acc.flatMap { combination ->
                list.map { ingredient -> combination + ingredient }
            }
        }

        return Result.success(combinations.map { ingredientCombination ->
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = filename,
                producedItems = listOf(MinecraftIdFactory.minecraftIdFromId(resultId)),
                requiredItems = ingredientCombination.map { MinecraftIdFactory.minecraftIdFromId(resultId) }
            )
        })
    }
}