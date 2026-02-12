package app.mcorg.pipeline.minecraft.extract

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.ServerPathResolvers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.nio.file.Path

data object ExtractItemsStep : Step<Pair<MinecraftVersion.Release, Path>, AppFailure, List<Item>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, List<Item>> {
        val idsFromTags = buildSet {
            addAll(GetItemIdsStep.process(input).getOrNull()?.flatten() ?: emptyList())
            addAll(GetBlockIdsStep.process(input).getOrNull()?.flatten() ?: emptyList())
        }

        if (idsFromTags.isEmpty()) {
            logger.warn("No item or block IDs extracted for version {}", input.first)
            return Result.success(emptyList())
        }

        return Result.success(
            idsFromTags.distinct()
                .filter { id -> !id.startsWith("#") }
                .map { Item(it, ExtractNamesStep.getName(input, it)) }
        )
    }

}

private data object GetItemIdsStep : ParseFilesRecursivelyStep<List<String>>() {
    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, List<List<String>>> {
        return super.process(input.first to ServerPathResolvers.resolveItemTagsPath(input.second, input.first))
    }

    override suspend fun parseFile(content: String, filename: String): Result<AppFailure, List<String>> {
        return ParseTagFile.process(content)
    }
}

private data object GetBlockIdsStep : ParseFilesRecursivelyStep<List<String>>() {
    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, List<List<String>>> {
        return super.process(input.first to ServerPathResolvers.resolveBlockTagsPath(input.second, input.first))
    }

    override suspend fun parseFile(content: String, filename: String): Result<AppFailure, List<String>> {
        return ParseTagFile.process(content)
    }
}

private data object ParseTagFile : Step<String, AppFailure, List<String>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    override suspend fun process(input: String): Result<AppFailure, List<String>> {
        val json = try {
            Json.parseToJsonElement(input)
        } catch (e: Exception) {
            logger.error("Error parsing JSON from tag file $input", e)
            return Result.failure(AppFailure.FileError(this.javaClass))
        }

        val itemIds = json.jsonObject["values"]?.let { valuesElement ->
            valuesElement.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        } ?: run {
            logger.error("No 'values' array found in tag file $input")
            return Result.failure(AppFailure.FileError(this.javaClass))
        }

        return Result.Success(itemIds)
    }
}