package app.mcorg.pipeline.minecraft.extract.recipe

import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.minecraft.extract.getResult
import app.mcorg.pipeline.minecraft.extract.objectResult
import app.mcorg.pipeline.minecraft.extract.primitiveResult
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

object RecipeQuantityParser {
    private val logger = LoggerFactory.getLogger(RecipeQuantityParser::class.java)

    /**
     * Parses the result quantity from a recipe JSON.
     *
     * For versions 1.18-1.19: "result": {"item": "minecraft:boat"} → defaults to 1
     * For versions 1.20+: "result": {"count": 4, "id": "minecraft:acacia_planks"} → uses count
     *
     * @param json The recipe JSON element
     * @param filename The filename for error reporting
     * @return ResourceQuantity.ItemQuantity with the parsed count, or Unknown on failure
     */
    suspend fun parseResultQuantity(json: JsonElement, filename: String): ResourceQuantity {
        // Try to extract count from result object
        val countResult = json.objectResult(filename)
            .flatMap { it.getResult("result", filename) }
            .flatMap { it.objectResult(filename) }
            .flatMap { it.getResult("count", filename) }
            .flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content.toIntOrNull() } }

        return when (countResult) {
            is Result.Success -> {
                val count = countResult.value
                if (count != null && count > 0) {
                    ResourceQuantity.ItemQuantity(count)
                } else {
                    logger.warn("Invalid count ($count) in recipe $filename, defaulting to 1")
                    ResourceQuantity.ItemQuantity(1)
                }
            }
            is Result.Failure -> {
                // Count field missing or invalid - default to 1 (common in older versions)
                ResourceQuantity.ItemQuantity(1)
            }
        }
    }

    /**
     * Returns the quantity for recipe ingredients.
     *
     * Minecraft recipes don't specify ingredient quantities - each ingredient slot always represents 1 item.
     *
     * @return ResourceQuantity.ItemQuantity(1)
     */
    fun ingredientQuantity(): ResourceQuantity {
        return ResourceQuantity.ItemQuantity(1)
    }
}
