package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

object ShapelessRecipeParser {
    private val logger = LoggerFactory.getLogger(ShapelessRecipeParser::class.java)

    suspend fun parse(
        json: JsonElement,
        filename: String
    ): Result<ExtractionFailure, ResourceSource> {
        val craftingValues = CraftingValuesParser.parse(json, filename)

        if (craftingValues is Result.Failure) {
            logger.warn("Shapeless recipe missing ingredients or result id in $filename")
            return craftingValues
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
