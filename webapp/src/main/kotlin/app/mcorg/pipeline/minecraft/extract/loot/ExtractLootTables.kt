package app.mcorg.pipeline.minecraft.extract.loot

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.ServerPathResolvers
import app.mcorg.pipeline.minecraft.extract.ExtractNamesStep
import app.mcorg.pipeline.minecraft.extract.ParseFilesRecursivelyStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.nio.file.Path

data object ExtractLootTables : ParseFilesRecursivelyStep<ResourceSource>() {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, List<ResourceSource>> {
        val version = input.first
        val basePath = input.second
        val namesInput = version to basePath.resolve("assets/minecraft")

        // Pre-load names cache to avoid race conditions during concurrent file parsing
        ExtractNamesStep.getNames(namesInput)

        return super.process(version to ServerPathResolvers.resolveLootTablesPath(basePath))
            .map { sources ->
                sources.map { source ->
                    source.copy(
                        producedItems = source.producedItems.map { item ->
                            item.copy(
                                name = ExtractNamesStep.getName(namesInput, item.id)
                            )
                        }
                    )
                }
            }
    }

    override suspend fun parseFile(content: String, filename: String): Result<AppFailure, List<ResourceSource>> {
        val json = try {
            Json.parseToJsonElement(content)
        } catch (e: Exception) {
            logger.error("Error parsing JSON from tag file $content", e)
            return Result.failure(AppFailure.FileError(this.javaClass))
        }

        return when (val type = json.jsonObject["type"]?.jsonPrimitive?.contentOrNull) {
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
                parseStandardLootTableStructure(json)
            }
            else -> {
                logger.warn("Unknown loot table type: $type")
                Result.success(ResourceSource(
                    type = ResourceSource.SourceType.UNKNOWN
                ))
            }
        }.mapSuccess { if (it.producedItems.isNotEmpty()) listOf(it) else emptyList() }
    }

    private fun parseStandardLootTableStructure(json: JsonElement): Result<AppFailure, ResourceSource> {
        val parsed = buildSet {
            json.jsonObject["pools"]?.jsonArray?.forEach { pool ->
                pool.jsonObject["entries"]?.jsonArray?.forEach { entry ->
                    addAll(parseEntriesRecursively(entry.jsonObject))
                }
            }
        }

        return Result.success(ResourceSource(
            type = json.jsonObject["type"]?.jsonPrimitive?.content?.let {
                ResourceSource.SourceType.of(it)
            } ?: ResourceSource.SourceType.UNKNOWN,
            producedItems = parsed.map { Item(id = it, name = it) }
        ))
    }

    private fun parseEntriesRecursively(json: JsonElement): Set<String> {
        return buildSet {
            when (val type = json.jsonObject["type"]?.jsonPrimitive?.contentOrNull) {
                "minecraft:item", "minecraft:dynamic" -> {
                    json.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.let { itemId ->
                        add(itemId)
                    }
                }
                "minecraft:empty", "minecraft:tag" -> {
                    // Do nothing for empty entries
                }
                "minecraft:loot_table" -> {
                    when (val value = json.jsonObject["value"]) {
                        is JsonPrimitive -> add(value.jsonPrimitive.content)
                        is JsonObject -> value["pools"]?.jsonArray?.forEach { pool ->
                            pool.jsonObject["entries"]?.jsonArray?.forEach { entry ->
                                addAll(parseEntriesRecursively(entry.jsonObject))
                            }
                        }
                        else -> logger.warn("Unknown value type in loot_table entry: ${value?.javaClass}")
                    }
                }
                "minecraft:alternatives" -> {
                    // Recursively parse children entries
                    json.jsonObject["children"]?.jsonArray?.forEach { childEntry ->
                        addAll(parseEntriesRecursively(childEntry))
                    }
                }
                else -> logger.warn("Unknown entry type in loot table: $type")
            }
        }
    }
}