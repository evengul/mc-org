package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.extract.primitiveResult
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

/**
 * Parses `minecraft:crafting_imbue` recipes introduced in 26.1.
 *
 * Shape:
 * ```
 * {
 *   "type": "minecraft:crafting_imbue",
 *   "material": "minecraft:arrow",
 *   "source": "minecraft:lingering_potion",
 *   "result": { "count": 8, "id": "minecraft:tipped_arrow" }
 * }
 * ```
 *
 * Semantics: `result.count` × material + 1 × source → `result.count` × result. The material
 * scales with the result count (e.g. 8 arrows per batch of 8 tipped arrows), while the source
 * is consumed once per batch. This is the intrinsic replacement for the old
 * `crafting_special_tippedarrow` recipe.
 */
object CraftingImbueParser {
    private val logger = LoggerFactory.getLogger(CraftingImbueParser::class.java)

    suspend fun parse(
        json: JsonElement,
        filename: String
    ): Result<ExtractionFailure, ResourceSource> {
        val material = json.objectResult(filename)
            .flatMap { it.getResult("material", filename) }
            .flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
        val source = json.objectResult(filename)
            .flatMap { it.getResult("source", filename) }
            .flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
        val resultId = RecipeItemIdParser.parse(json, filename)

        if (material is Result.Failure || source is Result.Failure || resultId is Result.Failure) {
            logger.warn("Crafting imbue recipe missing material, source, or result id in $filename")
            return Result.failure(
                ExtractionFailure.Multiple(
                    buildList {
                        if (material is Result.Failure) add(material.error)
                        if (source is Result.Failure) add(source.error)
                        if (resultId is Result.Failure) add(resultId.error)
                    }
                )
            )
        }

        val resultQuantity = RecipeQuantityParser.parseResultQuantity(json, filename)
        // Material scales with result count: 8 arrows → 8 tipped arrows, 1 arrow → 1 tipped arrow, etc.
        val materialQuantity = when (resultQuantity) {
            is ResourceQuantity.ItemQuantity -> ResourceQuantity.ItemQuantity(resultQuantity.itemQuantity)
            else -> RecipeQuantityParser.ingredientQuantity()
        }

        return Result.success(
            ResourceSource(
                type = ResourceSource.SourceType.RecipeTypes.CRAFTING_IMBUE,
                filename = filename,
                requiredItems = listOf(
                    MinecraftIdFactory.minecraftIdFromId(material.getOrThrow()) to materialQuantity,
                    MinecraftIdFactory.minecraftIdFromId(source.getOrThrow()) to RecipeQuantityParser.ingredientQuantity()
                ),
                producedItems = listOf(
                    MinecraftIdFactory.minecraftIdFromId(resultId.getOrThrow()) to resultQuantity
                )
            )
        )
    }
}
