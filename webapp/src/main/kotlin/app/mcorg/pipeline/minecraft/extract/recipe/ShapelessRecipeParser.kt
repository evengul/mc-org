package app.mcorg.pipeline.minecraft.extract.recipe

import app.mcorg.domain.model.resources.ResourceQuantity
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
    ): Result<AppFailure, ResourceSource> {
        val craftingValues = CraftingValuesParser.parse(json, filename)

        if (craftingValues is Result.Failure) {
            logger.warn("Shapeless recipe missing ingredients or result id in $filename")
            return Result.failure(AppFailure.FileError(ExtractRecipesStep.javaClass, filename))
        }

        val (resultId, ingredientList) = craftingValues.getOrThrow()

        val resultQuantity = RecipeQuantityParser.parseResultQuantity(json, filename)

        // Flatten the ingredient list (each inner list has only one item since tags aren't expanded)
        val ingredients = ingredientList.map { it.first() }

        // Count occurrences of each ingredient
        val ingredientCounts = ingredients.groupBy { it }.map { (ingredient, occurrences) ->
            MinecraftIdFactory.minecraftIdFromId(ingredient) to ResourceQuantity.ItemQuantity(occurrences.size)
        }

        return Result.success(ResourceSource(
            type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS,
            filename = filename,
            producedItems = listOf(MinecraftIdFactory.minecraftIdFromId(resultId) to resultQuantity),
            requiredItems = ingredientCounts
        ))
    }
}