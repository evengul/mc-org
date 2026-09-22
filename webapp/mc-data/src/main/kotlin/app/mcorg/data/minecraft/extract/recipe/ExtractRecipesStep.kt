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
import app.mcorg.pipeline.Step
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

data object ExtractRecipesStep : Step<ExtractionContext, ExtractionFailure, List<ResourceSource>> {
    private val logger = LoggerFactory.getLogger(javaClass)
    override suspend fun process(input: ExtractionContext): Result<ExtractionFailure, List<ResourceSource>> {
        // Built once per version, off the same tag registry withNames reads: a recipe's inline
        // alternative list is spelled as a `#mcorg:choice/…` tag by the parsers, and any such set
        // that vanilla already names becomes that vanilla tag here rather than a second name for
        // the same question (MCO-486).
        val canonicaliser = ChoiceTagCanonicaliser.from(input)
        return parseJsonFilesRecursively(input.version, ServerPathResolvers.resolveRecipesPath(input.root, input.version)) { content, filename ->
            parseFile(content, filename)
        }
            .map { sources ->
                sources.map { canonicaliser.canonicalise(it).withNames(input) }
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

                // Ignored on purpose: each writes a *component* onto an item whose id does not
                // change, so flattened to ids they are all `x -> x`. 26.3 moved brewing out of
                // game code into 279 datapack recipes and it joins them for the same reason —
                // `{"input": potion{awkward}, "reagent": sugar, "output": potion{swiftness}}`
                // reads as `minecraft:potion + minecraft:sugar -> minecraft:potion`, since every
                // potion in the game shares one item id and differs only in `potion_contents`.
                //
                // Extracting that as written would add 279 self-loops and make any potion look
                // reachable from any other. Nothing regresses by skipping it: brewing was
                // hardcoded and absent from the data before 26.3, so no potion has ever been
                // obtainable in a plan. Making them obtainable needs component-aware node
                // identity in the graph, which is a graph-shape change, not a parser one —
                // MCO-567, follow-up MCO-568.
                "minecraft:brewing",
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
