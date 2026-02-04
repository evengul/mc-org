package app.mcorg.pipeline.minecraft.extract.loot

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.extract.arrayResult
import app.mcorg.pipeline.minecraft.extract.getResult
import app.mcorg.pipeline.minecraft.extract.objectResult
import app.mcorg.pipeline.minecraft.extract.primitiveResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory

data class LootTableParser(
    private val poolParser: PoolParser,
    private val entryParser: EntryParser,
) {
    init {
        poolParser.entryParser = entryParser
        entryParser.poolParser = poolParser
    }

    private val logger = LoggerFactory.getLogger(LootTableParser::class.java)

    suspend fun parse(json: JsonElement, filename: String): Result<AppFailure, ResourceSource> {
        val type = json.objectResult(filename)
            .flatMap { it.getResult("type", filename) }
            .flatMap { it.primitiveResult(filename) }
            .flatMap { ResourceSource.SourceType.of(it.content)?.let { type -> Result.success(type) } ?: Result.failure(
                AppFailure.FileError(ExtractLootTables.javaClass, filename))
            }

        if (json is JsonObject && json.jsonObject["pools"] == null) {
            // Loot table with no pools produces no items
            return Result.success(ResourceSource(
                type = type.getOrThrow(),
                filename = filename,
                producedItems = emptyList()
            ))
        }

        val result = json.objectResult(filename)
            .flatMap { it.getResult("pools", filename) }
            .flatMap { it.arrayResult(filename) }
            .flatMap { poolParser.parsePool(it, filename) }

        if (type is Result.Failure) {
            logger.warn("Error parsing type from loot file: $filename")
            return type
        }

        if (result is Result.Failure) {
            logger.warn("Error parsing entries from loot file: $filename")

            return result
        }

        return Result.success(ResourceSource(
            type = type.getOrThrow(),
            filename = filename,
            producedItems = result.getOrThrow().map { id ->
                if (id.startsWith("#")) {
                    MinecraftTag(
                        id = id,
                        name = "",
                        content = emptyList()
                    ) to ResourceQuantity.Unknown
                } else {
                    Item(
                        id = id,
                        name = ""
                    ) to ResourceQuantity.Unknown
                }
            }
        ))
    }
}