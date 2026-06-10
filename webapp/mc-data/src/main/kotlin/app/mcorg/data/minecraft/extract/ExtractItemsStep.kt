package app.mcorg.data.minecraft.extract

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.data.minecraft.ServerPathResolvers
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.nio.file.Path

data object ExtractItemsStep : Step<ExtractionContext, ExtractionFailure, List<Item>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: ExtractionContext): Result<ExtractionFailure, List<Item>> {
        val idsFromTags = buildSet {
            addAll(tagValues(input.version, ServerPathResolvers.resolveItemTagsPath(input.root, input.version)))
            addAll(tagValues(input.version, ServerPathResolvers.resolveBlockTagsPath(input.root, input.version)))
        }

        if (idsFromTags.isEmpty()) {
            logger.warn("No item or block IDs extracted for version {}", input.version)
            return Result.success(emptyList())
        }

        return Result.success(
            idsFromTags
                .filter { id -> !id.startsWith("#") }
                .map { Item(it, input.nameOf(it)) }
        )
    }

    private suspend fun tagValues(version: MinecraftVersion.Release, path: Path): List<String> =
        parseJsonFilesRecursively(version, path) { content, filename -> parseTagFile(content, filename) }
            .getOrNull()?.flatten() ?: emptyList()

    private fun parseTagFile(content: String, filename: String): Result<ExtractionFailure, List<String>> {
        val json = try {
            Json.parseToJsonElement(content)
        } catch (e: Exception) {
            logger.error("Error parsing JSON from tag file $filename", e)
            return Result.failure(ExtractionFailure.JsonFailure.ParseError(content, filename))
        }

        val itemIds = json.jsonObject["values"]?.let { valuesElement ->
            valuesElement.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        } ?: run {
            logger.error("No 'values' array found in tag file $filename")
            return Result.failure(ExtractionFailure.JsonFailure.KeyNotFound(json, "values", filename))
        }

        return Result.Success(itemIds)
    }
}
