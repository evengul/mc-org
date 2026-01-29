package app.mcorg.pipeline.minecraft.extract.recipe

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.ServerPathResolvers
import app.mcorg.pipeline.minecraft.extract.ExtractNamesStep
import app.mcorg.pipeline.minecraft.extract.ExtractTagsStep
import app.mcorg.pipeline.minecraft.extract.ParseFilesRecursivelyStep
import app.mcorg.pipeline.minecraft.extract.getResult
import app.mcorg.pipeline.minecraft.extract.objectResult
import app.mcorg.pipeline.minecraft.extract.primitiveResult
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path

data object ExtractRecipesStep : ParseFilesRecursivelyStep<ResourceSource>() {
    private val logger = LoggerFactory.getLogger(javaClass)
    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, List<ResourceSource>> {
        val version = input.first
        val basePath = input.second

        ExtractNamesStep.getNames(input)
        ExtractTagsStep.process(input)

        // Expand input and outputs of recipes based on tags so we get the complete set of possible recipes

        return super.process(version to ServerPathResolvers.resolveRecipesPath(basePath, version))
            .map { sources ->
                sources.map { it.withNames(input) }
            }
    }

    private suspend fun ResourceSource.withNames(namesInput: Pair<MinecraftVersion.Release, Path>): ResourceSource {
        return this.copy(
            requiredItems = this.requiredItems.map { item ->
                item.withNames(namesInput)
            },
            producedItems = this.producedItems.map { item ->
                item.withNames(namesInput)
            }
        )
    }

    private suspend fun MinecraftId.withNames(namesInput: Pair<MinecraftVersion.Release, Path>): MinecraftId {
        return when (this) {
            is Item -> copy(
                name = ExtractNamesStep.getName(namesInput, this.id)
            )
            is MinecraftTag -> copy(
                name = ExtractTagsStep.getNameOfTag(this.id),
                content = ExtractTagsStep.getContentOfTag(namesInput.first, this.id).map { taggedItem ->
                    Item(
                        id = taggedItem,
                        name = ExtractNamesStep.getName(namesInput, taggedItem)
                    )
                }
            )
        }
    }

    override suspend fun parseFile(
        content: String,
        filename: String
    ): Result<AppFailure, List<ResourceSource>> {
        if (content.isEmpty()) {
            logger.warn("Empty recipe file: $filename")
            return Result.success(
                listOf(
                    ResourceSource(
                        type = ResourceSource.SourceType.UNKNOWN,
                        filename = filename
                    )
                )
            )
        }
        val json = try {
            Json.parseToJsonElement(content)
        } catch (e: Exception) {
            logger.error("Error parsing JSON from recipe file $content", e)
            return Result.failure(AppFailure.FileError(this.javaClass))
        }

        return try {
            val typeResult = json.objectResult(filename).flatMap { it.getResult("type", filename) }.flatMap { it.primitiveResult(filename) }
                .mapSuccess { it.content }
            if (typeResult is Result.Failure) {
                logger.warn("Recipe file $filename missing 'type' field")
                return Result.success(
                    listOf(
                        ResourceSource(
                            type = ResourceSource.SourceType.UNKNOWN,
                            filename = filename
                        )
                    )
                )
            }
            when (val type = typeResult.getOrThrow()) {
                "minecraft:crafting_shaped" -> ShapedRecipeParser.parse(json, filename)
                "minecraft:crafting_shapeless" -> ShapelessRecipeParser.parse(json, filename)
                "minecraft:smithing_transform",
                "minecraft:smithing" -> SmithingTransformParser.parse(json, filename)
                "minecraft:crafting_transmute" -> TransmuteRecipeParser.parse(json, filename)
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
                "minecraft:crafting_decorated_pot" -> Result.success(
                    listOf(
                        ResourceSource(
                            type = ResourceSource.SourceType.RecipeTypes.IGNORED,
                            filename = filename
                        )
                    )
                )

                else -> {
                    if (type.contains("_special_")) {
                        Result.success(
                            listOf(
                                ResourceSource(
                                    type = ResourceSource.SourceType.RecipeTypes.IGNORED,
                                    filename = filename
                                )
                            )
                        )
                    } else {
                        logger.warn("Unknown recipe type: $type in file $filename")
                        Result.success(
                            listOf(
                                ResourceSource(
                                    type = ResourceSource.SourceType.UNKNOWN,
                                    filename = filename
                                )
                            )
                        )
                    }
                }
            }.mapSuccess { fileSources -> fileSources.filter { source -> source.type != ResourceSource.SourceType.UNKNOWN && source.producedItems.isNotEmpty() } }
        } catch (e: Exception) {
            logger.error("Error parsing recipe file $filename", e)
            Result.failure(AppFailure.FileError(this.javaClass))
        }
    }
}