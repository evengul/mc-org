package app.mcorg.data.minecraft.extract.loot

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceQuantity
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

    suspend fun parseEntry(entry: JsonElement, filename: String): Result<ExtractionFailure, LootEntry> {
        val type = entry.objectResult(filename)
            .flatMap { it.getResult("type", filename) }
            .flatMap { it.primitiveResult(filename) }
            .mapSuccess { it.content }

        if (type is Result.Failure) {
            logger.warn("Error parsing entry type in loot table for file: $filename")
            return type
        }

        return when (val typeValue = type.getOrThrow()) {
            "minecraft:empty" -> parseEmpty(entry)
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

    fun parseEmpty(entry: JsonElement): Result<ExtractionFailure, LootEntry> {
        return Result.success(LootEntry(weight = LootNumbers.weightOf(entry), drops = emptyList()))
    }

    suspend fun parseItem(item: JsonElement, filename: String): Result<ExtractionFailure, LootEntry> {
        val name = item.jsonObject
            .getResult("name", filename)
            .flatMap { it.primitiveResult(filename) }
            .mapSuccess { primitive ->
                LootEntry(
                    weight = LootNumbers.weightOf(item),
                    drops = listOf(LootDrop(primitive.content, LootNumbers.countAfterFunctions(item)))
                )
            }

        if (name is Result.Failure) {
            logger.warn("Error parsing item in loot table for file: $filename")
        }

        return name
    }

    suspend fun parseDynamic(dynamic: JsonElement, filename: String): Result<ExtractionFailure, LootEntry> {
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
                        return parseEmpty(dynamic)
                    }
                    "minecraft:sherds" -> {
                        return Result.success(
                            LootEntry(
                                weight = LootNumbers.weightOf(dynamic),
                                drops = listOf(LootDrop("#minecraft:decorated_pot_sherds", null))
                            )
                        )
                    }
                    else -> {
                        logger.warn("Unhandled dynamic entry '${name.value}' in loot table for file: $filename")
                        return Result.failure(ExtractionFailure.JsonFailure.UnknownValue(value, "name", dynamic, filename))
                    }
                }
            }
        }
    }

    suspend fun parseTag(tag: JsonElement, filename: String): Result<ExtractionFailure, LootEntry> {
        val name = tag.jsonObject
            .getResult("tag", filename)
            .recover { tag.jsonObject.getResult("name", filename) }
            .flatMap { it.primitiveResult(filename) }
            .mapSuccess { primitive ->
                LootEntry(
                    weight = LootNumbers.weightOf(tag),
                    drops = listOf(LootDrop("#${primitive.content}", LootNumbers.countAfterFunctions(tag)))
                )
            }

        if (name is Result.Failure) {
            logger.warn("Error parsing tag in loot table for file: $filename")
        }

        return name
    }

    /**
     * Alternatives select the first child whose conditions match. The last child
     * is the unconditional fallback and carries the entry's yield; earlier
     * (conditional) children's items are still recorded as obtainable, but with
     * unknown yield — their trigger probability isn't in the data.
     */
    suspend fun parseAlternatives(alternatives: JsonElement, filename: String): Result<ExtractionFailure, LootEntry> {
        val children = alternatives.jsonObject.getResult("children", filename)
            .flatMap { it.arrayResult(filename) }
            .flatMap { parseEntries(it, filename) }

        if (children is Result.Failure) {
            logger.warn("Error parsing alternatives children in loot table for file: $filename")
            return children
        }

        val parsed = children.getOrThrow()
        val fallback = parsed.lastOrNull()?.drops.orEmpty()
        val conditional = parsed.dropLast(1)
            .flatMap { it.drops }
            .map { it.copy(countPerSelection = null) }

        return Result.success(
            LootEntry(
                weight = LootNumbers.weightOf(alternatives),
                drops = conditional + fallback
            )
        )
    }

    suspend fun parseLootTable(lootTable: JsonElement, filename: String): Result<ExtractionFailure, LootEntry> {
        val weight = LootNumbers.weightOf(lootTable)
        val value = lootTable.jsonObject.getResult("value", filename)
            .recover { lootTable.jsonObject.getResult("name", filename) }

        if (value is Result.Failure) {
            logger.warn("Error parsing loot_table value in loot table for file: $filename")
            return value
        }

        return when (val element = value.getOrThrow()) {
            is JsonPrimitive -> {
                val reference = element.content
                if (reference.contains("/")) {
                    parseReferencedTable(reference, weight, filename)
                } else {
                    Result.success(
                        LootEntry(weight, listOf(LootDrop(reference, LootNumbers.countAfterFunctions(lootTable))))
                    )
                }
            }
            is JsonObject -> element.jsonObject.getResult("pools", filename)
                .flatMap { it.arrayResult(filename) }
                .flatMap { poolParser.parsePools(it, filename) }
                .mapSuccess { yields ->
                    LootEntry(weight, yields.map { (id, expected) -> LootDrop(id, expected) })
                }
            else -> {
                logger.warn("Unknown loot_table value type in file: $filename")
                Result.failure(
                    ExtractionFailure.JsonFailure.UnsupportedType(
                        element.javaClass.simpleName, "{value,name}", lootTable, filename
                    )
                )
            }
        }
    }

    private suspend fun parseReferencedTable(
        reference: String,
        weight: Double,
        filename: String
    ): Result<ExtractionFailure, LootEntry> {
        val lootTablePath = findLootTableFilePath(reference)
        val content = try {
            withContext(Dispatchers.IO) {
                runInterruptible {
                    lootTablePath.toFile().readText()
                }
            }
        } catch (e: Exception) {
            logger.error("Error reading loot table file at $lootTablePath for file: $filename", e)
            return Result.failure(ExtractionFailure.FileReadFailure(reference))
        }

        val sources = parseFile(content, lootTablePath.toFile().name)
        if (sources is Result.Failure) {
            logger.warn("Error parsing referenced loot table file at $lootTablePath for file: $filename")
            return sources
        }

        val source = sources.getOrThrow()
        val drops = source.producedItems.map { (item, quantity) ->
            LootDrop(item.id, (quantity as? ResourceQuantity.ExpectedYield)?.expected)
        } + source.requiredItems.map { (item, _) -> LootDrop(item.id, null) }

        return Result.success(LootEntry(weight, drops))
    }

    suspend fun parseEntries(entries: JsonArray, filename: String): Result<ExtractionFailure, List<LootEntry>> {
        val mappedEntries = entries.map { entry -> parseEntry(entry, filename) }
        return if (mappedEntries.any { it is Result.Failure }) {
            Result.failure(ExtractionFailure.Multiple(mappedEntries.filter { it is Result.Failure }.mapNotNull { it.errorOrNull() }))
        } else {
            Result.success(mappedEntries.filterIsInstance<Result.Success<LootEntry>>().map { it.value })
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
