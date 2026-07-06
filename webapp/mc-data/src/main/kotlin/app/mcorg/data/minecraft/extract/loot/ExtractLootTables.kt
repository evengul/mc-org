package app.mcorg.data.minecraft.extract.loot

import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.ServerPathResolvers
import app.mcorg.data.minecraft.extract.ExtractionContext
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.extract.parseJsonFilesRecursively
import app.mcorg.data.minecraft.extract.primitiveResult
import app.mcorg.data.minecraft.extract.withNames
import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.domain.pipeline.Step
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Infested blocks' `minecraft:block` loot tables list a Silk Touch drop of the *base* block
 * (e.g. `infested_stone_bricks.json` drops `minecraft:stone_bricks`), but `InfestedBlock`
 * overrides destroy handling in game code: without Silk Touch it drops nothing (spawns a
 * silverfish instead), and with Silk Touch it drops the infested block itself, never the base
 * block. The loot-table entry is a phantom the JSON data can't reveal, and its filename
 * (`infested_stone_bricks`) doesn't match the item it phantom-drops (`stone_bricks`), so
 * without this guard it reads as a legitimate raw-gather source and wins over crafting.
 */
internal fun isPhantomInfestedBlockLoot(filename: String): Boolean {
    val stem = filename.substringAfterLast('/').substringBeforeLast('.')
    return stem.startsWith("infested_")
}

data object ExtractLootTables : Step<ExtractionContext, ExtractionFailure, List<ResourceSource>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: ExtractionContext): Result<ExtractionFailure, List<ResourceSource>> {
        val lootTableParser = LootTableParser(input.root, input.version)

        return parseJsonFilesRecursively(input.version, ServerPathResolvers.resolveLootTablesPath(input.root, input.version)) { content, filename ->
            parseFile(lootTableParser, content, filename)
        }
            .map { sources ->
                sources.map { it.withNames(input) }
                    .filter { it.type != ResourceSource.SourceType.RecipeTypes.IGNORED && it.producedItems.isNotEmpty() }
            }
    }

    private suspend fun parseFile(lootTableParser: LootTableParser, content: String, filename: String): Result<ExtractionFailure, ResourceSource> {
        val json = try {
            Json.parseToJsonElement(content)
        } catch (e: Exception) {
            logger.error("Error parsing JSON from tag file $content", e)
            return Result.failure(ExtractionFailure.JsonFailure.ParseError(content, filename))
        }

        val type = json.objectResult(filename)
            .flatMap { it.getResult("type", filename) }
            .flatMap { it.primitiveResult(filename) }
            .mapSuccess { it.content }

        if (type is Result.Failure) {
            logger.warn("Error parsing type from loot file: $filename")
            return type
        }

        return when (val stringType = type.getOrThrow()) {
            "minecraft:block" -> {
                if (isPhantomInfestedBlockLoot(filename)) {
                    logger.debug("Dropping phantom infested-block loot table: $filename")
                    Result.success(
                        ResourceSource(
                            type = ResourceSource.SourceType.LootTypes.BLOCK,
                            filename = filename,
                            producedItems = emptyList()
                        )
                    )
                } else {
                    lootTableParser.parse(json, filename)
                }
            }
            "minecraft:archaeology",
            "minecraft:fishing",
            "minecraft:block_interact",
            "minecraft:barter",
            "minecraft:entity",
            "minecraft:entity_interact",
            "minecraft:chest",
            "minecraft:gift",
            "minecraft:equipment",
            "minecraft:shearing" -> {
                lootTableParser.parse(json, filename)
            }
            else -> {
                logger.warn("Unknown loot table type: $type")
                Result.failure(ExtractionFailure.JsonFailure.UnknownValue(stringType, "type", json, filename))
            }
        }
    }
}
