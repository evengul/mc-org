package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.extract.arrayResult
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

object CraftingValuesParser {
    private val logger = LoggerFactory.getLogger(CraftingValuesParser::class.java)

    suspend fun parse(
        json: JsonElement,
        filename: String
    ) : Result<ExtractionFailure, Pair<String, List<List<String>>>> {
        val resultIdResult = RecipeItemIdParser.parse(json, filename)

        val ingredientListResult = json.objectResult(filename).flatMap { it.getResult("ingredients", filename) }
            .flatMap { it.arrayResult(filename) }
            .mapSuccess { ingredients ->
                ingredients.mapNotNull { value ->
                    when (value) {
                        is JsonArray -> value.mapNotNull { alternative ->
                            parseItemRef(alternative).also {
                                if (it == null) logger.warn("Unknown ingredient value in array in recipe: $alternative in file $filename")
                            }
                        }
                        else -> parseItemRef(value)?.let { listOf(it) }.also {
                            if (it == null) logger.warn("Unknown ingredient value in recipe: $value in file $filename")
                        }
                    }
                }
            }

        if (resultIdResult is Result.Failure || ingredientListResult is Result.Failure) {
            logger.warn("Crafting recipe missing ingredients or result id in $filename")
            return Result.failure(
                ExtractionFailure.Multiple(
                    buildList {
                        if (resultIdResult is Result.Failure) add(resultIdResult.error)
                        if (ingredientListResult is Result.Failure) add(ingredientListResult.error)
                    }
                )
            )
        }

        return Result.success(
            resultIdResult.getOrThrow() to ingredientListResult.getOrThrow()
        )
    }
}
