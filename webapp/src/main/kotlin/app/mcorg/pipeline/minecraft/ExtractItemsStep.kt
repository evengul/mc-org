package app.mcorg.pipeline.minecraft

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

data object ExtractItemsStep : Step<Pair<MinecraftVersion.Release, Path>, AppFailure, List<Item>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, List<Item>> {
        val names = cleanNames(GetNamesStep.process(input).getOrNull() ?: emptyMap())

        return ExtractTagFilePathsStep.process(input)
            .flatMap { ExtractItemsFromTagFilesStep.process(it) }
            .mapSuccess { ids ->
                ids.distinct()
                    .filter { id -> !id.startsWith("#") }
                    .map { Item(it, findNameForId(it, names)) }
            }
    }

    private fun cleanNames(names: Map<String, String>): Map<String, String> {
        return names.mapValues { entry ->
            entry.value + if (entry.key.contains("item.minecraft.")) {
                " (Item)"
            } else if (entry.key.contains("block.minecraft.")) {
                " (Block)"
            } else {
                ""
            }
        }.mapKeys { entry ->
            entry.key
                .replace("item.minecraft.", "minecraft:")
                .replace("block.minecraft.", "minecraft:")
        }
    }

    private fun findNameForId(id: String, names: Map<String, String>): String {
        if (names[id] == null) {
            if (id.contains("_wall_banner")) {
                return "Wall Banner"
            }
            if (id.contains("_smithing_template")) {
                return "Smithing Template"
            }
            logger.error("Could not find name for item with id $id. Will use ID as name, but this requires manual work to fix.")
            return id
        }
        return names[id]!!
    }
}

private data object GetNamesStep : Step<Pair<MinecraftVersion.Release, Path>, AppFailure, Map<String, String>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val keyWhiteList = listOf(
        "item.minecraft.",
        "block.minecraft."
    )

    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, Map<String, String>> {
        try {
            // input.second is the directory, we need to read lang/en_us.json from within it
            val enUsFile = input.second.resolve("lang").resolve("en_us.json")
            if (!enUsFile.toFile().exists()) {
                logger.error("en_us.json file does not exist at path: {}", enUsFile)
                return Result.failure(AppFailure.FileError(this.javaClass))
            }
            logger.info("Reading items from: {}", enUsFile)

            val content = enUsFile.toFile().readText()

            val map = Json.decodeFromString<LinkedHashMap<String, String>>(content)
                .filterKeys { key -> keyWhiteList.any { whiteListItem -> key.startsWith(whiteListItem) } }

            if (map.isEmpty()) {
                logger.warn("No item names extracted from en_us.json for version {}", input.first)
                return Result.failure(AppFailure.FileError(this.javaClass))
            }

            logger.info("Extracted {} items for version {}", map.size, input.first)
            return Result.success(map)
        } catch (e: Exception) {
            logger.error("Failed to extract items for version {}: {}", input.first, e.message, e)
            return Result.failure(AppFailure.FileError(this.javaClass))
        }
    }
}

private data object ExtractTagFilePathsStep : Step<Pair<MinecraftVersion.Release, Path>, AppFailure, List<Path>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, List<Path>> {
        return try {
            val blockTagsPath = ServerPathResolvers.resolveBlockTagsPath(input.second, input.first)
            val itemTagsPath = ServerPathResolvers.resolveItemTagsPath(input.second, input.first)

            val allFiles = buildList {
                if (Files.exists(blockTagsPath)) {
                    Files.walk(blockTagsPath).use { stream ->
                        stream.filter { Files.isRegularFile(it) }.forEach { add(it) }
                    }
                }
                if (Files.exists(itemTagsPath)) {
                    Files.walk(itemTagsPath).use { stream ->
                        stream.filter { Files.isRegularFile(it) }.forEach { add(it) }
                    }
                }
            }

            Result.Success(allFiles)
        } catch (e: Exception) {
            logger.error("Error extracting tag file paths from $input", e)
            Result.failure(AppFailure.FileError(this.javaClass))
        }
    }
}

private data object ExtractItemsFromTagFilesStep : Step<List<Path>, AppFailure, List<String>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    override suspend fun process(input: List<Path>): Result<AppFailure, List<String>> {
        val items = mutableListOf<String>()
        coroutineScope {
            input.map { file ->
                async(Dispatchers.IO) {
                    when (val parseResult = ParseTagFile.process(file)) {
                        is Result.Success -> {
                            val itemIds = parseResult.value
                            items.addAll(itemIds)
                        }

                        is Result.Failure -> {
                            logger.error("Error processing $file")
                        }
                    }
                }
            }.awaitAll()
        }

        return Result.Success(items)
    }
}

private data object ParseTagFile : Step<Path, AppFailure, List<String>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    override suspend fun process(input: Path): Result<AppFailure, List<String>> {
        val fileContent = try {
            withContext(Dispatchers.IO) {
                Files.readString(input)
            }
        } catch (e: Exception) {
            logger.error("Error reading tag file $input", e)
            return Result.failure(AppFailure.FileError(this.javaClass))
        }

        val json = try {
            Json.parseToJsonElement(fileContent)
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