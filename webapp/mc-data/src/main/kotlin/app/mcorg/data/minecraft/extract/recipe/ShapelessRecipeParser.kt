package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.domain.model.minecraft.MinecraftId
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

        // Resolve each ingredient slot: a single id -> that item/tag; a list of alternatives ->
        // a synthetic choice tag the user resolves (previously the first was silently taken).
        val ingredients: List<MinecraftId> = ingredientList.mapNotNull { alternatives ->
            when {
                alternatives.isEmpty() -> null
                alternatives.size == 1 -> MinecraftIdFactory.minecraftIdFromId(alternatives.single())
                else -> choiceTag(alternatives)
            }
        }

        // Count repeated slots, keyed by id so a tag and an item never collide.
        val ingredientCounts = ingredients.groupBy { it.id }.map { (_, occurrences) ->
            occurrences.first() to ResourceQuantity.ItemQuantity(occurrences.size)
        }

        return Result.success(ResourceSource(
            type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPELESS,
            filename = filename,
            producedItems = listOf(MinecraftIdFactory.minecraftIdFromId(resultId) to resultQuantity),
            requiredItems = ingredientCounts
        ))
    }
}
