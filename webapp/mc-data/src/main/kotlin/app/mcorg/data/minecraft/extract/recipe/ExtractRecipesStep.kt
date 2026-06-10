package app.mcorg.data.minecraft.extract.recipe

import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.ServerPathResolvers
import app.mcorg.data.minecraft.extract.ExtractionContext
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.extract.primitiveResult
import app.mcorg.data.minecraft.extract.parseJsonFilesRecursively
import app.mcorg.data.minecraft.extract.withNames
import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.domain.pipeline.Step
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

data object ExtractRecipesStep : Step<ExtractionContext, ExtractionFailure, List<ResourceSource>> {
    private val logger = LoggerFactory.getLogger(javaClass)
    override suspend fun process(input: ExtractionContext): Result<ExtractionFailure, List<ResourceSource>> {
        return parseJsonFilesRecursively(input.version, ServerPathResolvers.resolveRecipesPath(input.root, input.version)) { content, filename ->
            parseFile(content, filename)
        }
            .map { sources ->
                sources.map { it.withNames(input) }
                    .filter { it.producedItems.isNotEmpty() }
            }
    }

    private suspend fun parseFile(
        content: String,
        filename: String
    ): Result<ExtractionFailure, ResourceSource> {
        if (content.isEmpty()) {
            logger.warn("Empty recipe file: $filename")
            return Result.failure(ExtractionFailure.MissingContent(filename))
        }
        val json = try {
            Json.parseToJsonElement(content)
        } catch (e: Exception) {
            logger.error("Error parsing JSON from recipe file $content", e)
            return Result.failure(ExtractionFailure.JsonFailure.ParseError(content, filename))
        }

        return try {
            val typeResult = json.objectResult(filename).flatMap { it.getResult("type", filename) }.flatMap { it.primitiveResult(filename) }
                .mapSuccess { it.content }
            if (typeResult is Result.Failure) {
                logger.warn("Recipe file $filename missing 'type' field")
                return Result.failure(ExtractionFailure.JsonFailure.KeyNotFound(json, "type", filename))
            }
            when (val type = typeResult.getOrThrow()) {
                "minecraft:crafting_shaped" -> ShapedRecipeParser.parse(json, filename)
                "minecraft:crafting_shapeless" -> ShapelessRecipeParser.parse(json, filename)
                "minecraft:smithing_transform",
                "minecraft:smithing" -> SmithingTransformParser.parse(json, filename)
                "minecraft:crafting_transmute" -> TransmuteRecipeParser.parse(json, filename)
                "minecraft:crafting_imbue" -> CraftingImbueParser.parse(json, filename)
                "minecraft:smelting" -> SimpleRecipeParser.parse(
                    json,
                    ResourceSource.SourceType.RecipeTypes.SMELTING,
                    filename
                )

                "minecraft:blasting" -> SimpleRecipeParser.parse(
                    json,
                    ResourceSource.SourceType.RecipeTypes.BLASTING,
                    filename
                )

                "minecraft:smoking" -> SimpleRecipeParser.parse(json, ResourceSource.SourceType.RecipeTypes.SMOKING, filename)
                "minecraft:campfire_cooking" -> SimpleRecipeParser.parse(
                    json,
                    ResourceSource.SourceType.RecipeTypes.CAMPFIRE_COOKING,
                    filename
                )

                "minecraft:stonecutting" -> SimpleRecipeParser.parse(
                    json,
                    ResourceSource.SourceType.RecipeTypes.STONECUTTING,
                    filename
                )

                "minecraft:smithing_trim",
                "minecraft:crafting_decorated_pot",
                "minecraft:crafting_dye" -> Result.success(
                    ResourceSource(
                        type = ResourceSource.SourceType.RecipeTypes.IGNORED,
                        filename = filename
                    )
                )

                else -> {
                    if (type.contains("_special_")) {
                        Result.success(
                            ResourceSource(
                                type = ResourceSource.SourceType.RecipeTypes.IGNORED,
                                filename = filename
                            )
                        )
                    } else {
                        logger.warn("Unknown recipe type: $type in file $filename")
                        Result.failure(ExtractionFailure.JsonFailure.UnknownValue(type, "type", json, filename))
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error parsing recipe file $filename", e)
            Result.failure(ExtractionFailure.JsonFailure.ParseError(content, filename))
        }
    }
}
