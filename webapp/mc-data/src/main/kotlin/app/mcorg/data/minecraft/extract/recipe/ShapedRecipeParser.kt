package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.extract.arrayResult
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.extract.primitiveResult
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.coroutines.runBlocking
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

        // Parse the key to map symbols to ingredient IDs (tags stay as tags, not expanded)
        val keyResult = json.objectResult(filename)
            .flatMap { it.getResult("key", filename) }
            .flatMap { it.objectResult(filename) }
            .mapSuccess { keyObject ->
                keyObject.jsonObject.entries.associate { (symbol, ingredientJson) ->
                    val ingredientId = runBlocking {
                        when (ingredientJson) {
                            is JsonPrimitive -> ingredientJson.content
                            is JsonObject -> {
                                ingredientJson.jsonObject.getResult("item", filename)
                                    .recover { ingredientJson.jsonObject.getResult("tag", filename).mapSuccess { tag -> JsonPrimitive("#${tag.jsonPrimitive.content}") } }
                                    .recover { ingredientJson.jsonObject.getResult("key", filename) }
                                    .recover { ingredientJson.jsonObject.getResult("id", filename) }
                                    .flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
                                    .getOrNull()
                            }
                            else -> null
                        }
                    }
                    symbol.single() to ingredientId
                }
            }

        if (keyResult is Result.Failure) {
            logger.warn("Shaped recipe missing key in $filename")
            return keyResult
        }
        val symbolToIngredient = keyResult.getOrThrow()

        // Count occurrences of each ingredient based on pattern
        val ingredientCounts = mutableMapOf<String, Int>()
        symbolCounts.forEach { (symbol, count) ->
            val ingredient = symbolToIngredient[symbol]
            if (ingredient != null) {
                ingredientCounts[ingredient] = (ingredientCounts[ingredient] ?: 0) + count
            }
        }

        val requiredItems = ingredientCounts.map { (ingredient, count) ->
            MinecraftIdFactory.minecraftIdFromId(ingredient) to ResourceQuantity.ItemQuantity(count)
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
}
