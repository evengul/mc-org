package app.mcorg.data.minecraft.extract.loot

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceQuantity
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

data object ExtractLootTables : Step<ExtractionContext, ExtractionFailure, List<ResourceSource>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: ExtractionContext): Result<ExtractionFailure, List<ResourceSource>> {
        val lootTableParser = LootTableParser(input.root, input.version)

        return parseJsonFilesRecursively(input.version, ServerPathResolvers.resolveLootTablesPath(input.root, input.version)) { content, filename ->
            parseFile(lootTableParser, content, filename)
        }
            .map { sources ->
                sources.map { it.withNames(input) }
                    .filter { it.type != ResourceSource.SourceType.RecipeTypes.IGNORED && it.producedItems.isNotEmpty() } + hardcodedLoot()
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
            "minecraft:archaeology",
            "minecraft:fishing",
            "minecraft:block",
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

    private fun hardcodedLoot() = listOf(
        ResourceSource(
            type = ResourceSource.SourceType.LootTypes.ENTITY,
            filename = "wither.json",
            producedItems = listOf(
                Item("minecraft:nether_star", "Nether Star (Item)") to ResourceQuantity.ExpectedYield(1.0)
            )
        )
    )
}
