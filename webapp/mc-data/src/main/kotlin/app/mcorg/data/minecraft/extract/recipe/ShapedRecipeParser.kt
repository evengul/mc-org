package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.extract.arrayResult
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

object ShapedRecipeParser {
    private val logger = LoggerFactory.getLogger(ShapedRecipeParser::class.java)

    suspend fun parse(json: JsonElement, filename: String): Result<ExtractionFailure, ResourceSource> {
        // Parse result ID
        val resultIdResult = RecipeItemIdParser.parse(json, filename)
        if (resultIdResult is Result.Failure) {
            logger.warn("Shaped recipe missing result id in $filename")
            return resultIdResult
        }
        val resultId = resultIdResult.getOrThrow()
        val resultQuantity = RecipeQuantityParser.parseResultQuantity(json, filename)

        // Parse the pattern to count symbol occurrences
        val patternResult = json.objectResult(filename)
            .flatMap { it.getResult("pattern", filename) }
            .flatMap { it.arrayResult(filename) }
            .mapSuccess { patternArray ->
                // Join all pattern rows and count each symbol
                val patternString = patternArray.joinToString("") { it.jsonPrimitive.content }
                patternString.filter { it != ' ' } // Remove spaces
                    .groupBy { it }
                    .mapValues { (_, chars) -> chars.size }
            }

        if (patternResult is Result.Failure) {
            logger.warn("Shaped recipe missing pattern in $filename")
            return patternResult
        }
        val symbolCounts = patternResult.getOrThrow()

        // Parse the key to map symbols to ingredients (tags stay as tags; a list of
        // alternatives becomes a synthetic choice tag).
        val keyResult = json.objectResult(filename)
            .flatMap { it.getResult("key", filename) }
            .flatMap { it.objectResult(filename) }
            .mapSuccess { keyObject ->
                keyObject.entries.associate { (symbol, ingredientJson) ->
                    symbol.single() to parseKeyIngredient(ingredientJson)
                }
            }

        if (keyResult is Result.Failure) {
            logger.warn("Shaped recipe missing key in $filename")
            return keyResult
        }
        val symbolToIngredient = keyResult.getOrThrow()

        // Count occurrences of each ingredient based on pattern, keyed by id so a tag and an
        // item id never collide and the MinecraftId (with any tag members) is preserved.
        val ingredientCounts = LinkedHashMap<String, Pair<MinecraftId, Int>>()
        symbolCounts.forEach { (symbol, count) ->
            val ingredient = symbolToIngredient[symbol] ?: return@forEach
            val existing = ingredientCounts[ingredient.id]
            ingredientCounts[ingredient.id] = ingredient to ((existing?.second ?: 0) + count)
        }

        val requiredItems = ingredientCounts.values.map { (ingredient, count) ->
            ingredient to ResourceQuantity.ItemQuantity(count)
        }

        return Result.success(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_SHAPED,
                filename = filename,
                producedItems = listOf(MinecraftIdFactory.minecraftIdFromId(resultId) to resultQuantity),
                requiredItems = requiredItems,
            )
        )
    }

    /**
     * Resolves a key entry to a [MinecraftId]. Usually a single item or tag ref, but Minecraft
     * allows a list of alternatives (TNT's `#` is `[sand, red_sand]`); two or more become a
     * synthetic [choiceTag] the user resolves, a single-element list is just that item, and an
     * unresolvable entry is dropped (null).
     */
    private fun parseKeyIngredient(element: JsonElement): MinecraftId? = when (element) {
        is JsonArray -> {
            val members = element.mapNotNull { parseItemRef(it) }
            when {
                members.isEmpty() -> null
                members.size == 1 -> MinecraftIdFactory.minecraftIdFromId(members.single())
                else -> choiceTag(members)
            }
        }
        else -> parseItemRef(element)?.let { MinecraftIdFactory.minecraftIdFromId(it) }
    }
}
