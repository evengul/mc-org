package app.mcorg.data.minecraft.extract.loot

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.ServerPathResolvers
import app.mcorg.data.minecraft.extract.arrayResult
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.loot.ExtractLootTables.parseFile
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.extract.primitiveResult
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import java.nio.file.Path

data class EntryParser(
    val path: Path,
    val version: MinecraftVersion.Release,
) {
    lateinit var poolParser: PoolParser

    private val logger = LoggerFactory.getLogger(EntryParser::class.java)

    suspend fun parseEntry(entry: JsonElement, filename: String): Result<ExtractionFailure, Set<String>> {
        val type = entry.objectResult(filename)
            .flatMap { it.getResult("type", filename) }
            .flatMap { it.primitiveResult(filename) }
            .mapSuccess { it.content }

        if (type is Result.Failure) {
            logger.warn("Error parsing entry type in loot table for file: $filename")
            return type
        }

        return when (val typeValue = type.getOrThrow()) {
            "minecraft:empty" ->  parseEmpty()
            "minecraft:item" -> parseItem(entry, filename)
            "minecraft:dynamic" -> parseDynamic(entry, filename)
            "minecraft:tag" -> parseTag(entry, filename)
            "minecraft:alternatives" -> parseAlternatives(entry, filename)
            "minecraft:loot_table" -> parseLootTable(entry, filename)
            else -> {
                logger.warn("Unknown loot table type: $typeValue in file: $filename")
                Result.failure(ExtractionFailure.JsonFailure.UnknownValue(typeValue, "loot table type", entry, filename))
            }
        }
    }

    fun parseEmpty(): Result<ExtractionFailure, Set<String>> {
        return Result.success(emptySet())
    }

    suspend fun parseItem(item: JsonElement, filename: String): Result<ExtractionFailure, Set<String>> {
        val name = item.jsonObject
            .getResult("name", filename)
            .flatMap { it.primitiveResult(filename) }
            .mapSuccess { setOf(it.content) }

        if (name is Result.Failure) {
            logger.warn("Error parsing item in loot table for file: $filename")
        }

        return name
    }

    suspend fun parseDynamic(dynamic: JsonElement, filename: String): Result<ExtractionFailure, Set<String>> {
        val name = dynamic.jsonObject
            .getResult("name", filename)
            .flatMap { it.primitiveResult(filename) }
            .mapSuccess { it.content }

        when (name) {
            is Result.Failure -> {
                logger.warn("Error parsing dynamic entry in loot table for file: $filename")
                return name
            }
            is Result.Success -> {
                when (val value = name.value) {
                    "minecraft:contents" -> {
                        // Cannot determine item ids from dynamic contents. Usually used in shulker boxes.
                        return parseEmpty()
                    }
                    "minecraft:sherds" -> {
                        return Result.success(setOf("#minecraft:decorated_pot_sherds"))
                    }
                    else -> {
                        logger.warn("Unhandled dynamic entry '${name.value}' in loot table for file: $filename")
                        return Result.failure(ExtractionFailure.JsonFailure.UnknownValue(value, "name",dynamic, filename))
                    }
                }
            }
        }
    }

    suspend fun parseTag(tag: JsonElement, filename: String): Result<ExtractionFailure, Set<String>> {
        val name = tag.jsonObject
            .getResult("tag", filename)
            .recover { tag.jsonObject.getResult("name", filename) }
            .flatMap { it.primitiveResult(filename) }
            .mapSuccess { setOf("#${it.content}") }

        if (name is Result.Failure) {
            logger.warn("Error parsing tag in loot table for file: $filename")
        }

        return name
    }

    suspend fun parseAlternatives(alternatives: JsonElement, filename: String): Result<ExtractionFailure, Set<String>> {
        val children = alternatives.jsonObject.getResult("children", filename)
            .flatMap { it.arrayResult(filename) }
            .flatMap { parseEntries(it, filename) }

        if (children is Result.Failure) {
            logger.warn("Error parsing alternatives children in loot table for file: $filename")
        }

        return children
    }

    suspend fun parseLootTable(lootTable: JsonElement, filename: String): Result<ExtractionFailure, Set<String>> {
        val result = lootTable.jsonObject.getResult("value", filename)
            .recover { lootTable.jsonObject.getResult("name", filename) }
            .flatMap { value ->
                when (value) {
                    is JsonPrimitive -> value.primitiveResult(filename).mapSuccess { setOf(it.content) }
                    is JsonObject -> value.jsonObject.getResult("pools", filename)
                        .flatMap { it.arrayResult(filename) }
                        .flatMap { poolParser.parsePool(it, filename) }
                    else -> {
                        logger.warn("Unknown loot_table value type in file: $filename")
                        Result.failure(ExtractionFailure.JsonFailure.UnsupportedType(value.javaClass.simpleName, "{value,name}", lootTable, filename))
                    }
                }
            }

        return when (result) {
            is Result.Failure -> {
                logger.warn("Error parsing loot_table value in loot table for file: $filename")
                result
            }
            is Result.Success -> {
                if (result.value.size == 1) {
                    val single = result.getOrThrow().first()
                    if (single.contains("/")) {
                        val lootTablePath = findLootTableFilePath(single)
                        val content = try {
                            withContext(Dispatchers.IO) {
                                runInterruptible {
                                    lootTablePath.toFile().readText()
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Error reading loot table file at $lootTablePath for file: $filename", e)

                            return Result.failure(ExtractionFailure.FileReadFailure(single))
                        }

                        val sources = parseFile(content, lootTablePath.toFile().name)

                        if (sources is Result.Failure) {
                            logger.warn("Error parsing referenced loot table file at $lootTablePath for file: $filename")
                            sources
                        } else {
                            Result.success(sources.getOrThrow().let {
                                it.producedItems.map { item -> item.first.id } + it.requiredItems.map { item -> item.first.id }
                            }.toSet())
                        }
                    } else {
                        result
                    }
                } else {
                    result
                }
            }
        }
    }

    suspend fun parseEntries(entries: JsonArray, filename: String): Result<ExtractionFailure, Set<String>> {
        val mappedEntries = entries.map { entry -> parseEntry(entry, filename) }
        return if (mappedEntries.any { it is Result.Failure }) {
            Result.failure(ExtractionFailure.Multiple(mappedEntries.filter { it is Result.Failure }.mapNotNull { it.errorOrNull() }))
        } else {
            Result.success(mappedEntries.filter { it is Result.Success }.map { it as Result.Success }.flatMap { it.value }.toSet())
        }
    }

    private fun findLootTableFilePath(itemId: String): Path {
        val relativePath = itemId
            .removePrefix("minecraft:")
            .replace(":", "/") + ".json"
        return ServerPathResolvers.resolveLootTablesPath(path, version)
            .resolve(relativePath)
    }
}
