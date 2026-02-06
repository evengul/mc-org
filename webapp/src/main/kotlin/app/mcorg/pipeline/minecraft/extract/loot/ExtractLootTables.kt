package app.mcorg.pipeline.minecraft.extract.loot

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceQuantity
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

data object ExtractLootTables : ParseFilesRecursivelyStep<ResourceSource>() {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private lateinit var lootTableParser: LootTableParser

    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, List<ResourceSource>> {
        val version = input.first
        val basePath = input.second

        val entryParser = EntryParser(basePath, version)
        val poolParser = PoolParser()

        lootTableParser = LootTableParser(poolParser, entryParser)

        // Pre-load names cache to avoid race conditions during concurrent file parsing
        ExtractNamesStep.getNames(input)
        ExtractTagsStep.process(input)

        return super.process(version to ServerPathResolvers.resolveLootTablesPath(basePath, version))
            .map { addNames(input, it)+ hardcodedLoot() }
    }

    private suspend fun addNames(input: Pair<MinecraftVersion.Release, Path>, sources: List<ResourceSource>): List<ResourceSource> {
        return sources.map { source ->
            source.copy(
                producedItems = source.producedItems.map { itemAndQuantity ->
                    when (val item = itemAndQuantity.first) {
                        is MinecraftTag -> itemAndQuantity.copy(
                            first = item.copy(
                                name = ExtractTagsStep.getNameOfTag(itemAndQuantity.first.id),
                                content = ExtractTagsStep.getContentOfTag(version, itemAndQuantity.first.id)
                                    .map { Item(it, ExtractNamesStep.getName(input, it)) }
                            )
                        )
                        is Item -> itemAndQuantity.copy(
                            first = item.copy(
                                name = ExtractNamesStep.getName(input, itemAndQuantity.first.id)
                            )
                        )
                    }
                }
            )
        }.filter { it.type != ResourceSource.SourceType.RecipeTypes.IGNORED && it.producedItems.isNotEmpty() }
    }

    override suspend fun parseFile(content: String, filename: String): Result<AppFailure, ResourceSource> {
        val json = try {
            Json.parseToJsonElement(content)
        } catch (e: Exception) {
            logger.error("Error parsing JSON from tag file $content", e)
            return Result.failure(AppFailure.FileError(this.javaClass))
        }

        val type = json.objectResult(filename)
            .flatMap { it.getResult("type", filename) }
            .flatMap { it.primitiveResult(filename) }
            .mapSuccess { it.content }

        if (type is Result.Failure) {
            logger.warn("Error parsing type from loot file: $filename")
            return Result.failure(AppFailure.FileError(this.javaClass, filename))
        }

        return when (type.getOrThrow()) {
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
                Result.failure(AppFailure.FileError(javaClass, filename))
            }
        }
    }

    private fun hardcodedLoot() = listOf(
        ResourceSource(
            type = ResourceSource.SourceType.LootTypes.ENTITY,
            filename = "wither.json",
            producedItems = listOf(
                Item("minecraft:nether_star", "Nether Star (Item)") to ResourceQuantity.Unknown
            )
        )
    )
}