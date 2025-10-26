package app.mcorg.pipeline.minecraft

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path

data object ExtractMinecraftDataStep : Step<Pair<MinecraftVersion.Release, Path>, GetServerFilesFailure, ServerData> {
    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<GetServerFilesFailure, ServerData> {
        val result = ExtractItemsDataStep.process(input)

        if (result is Result.Failure) {
            return Result.failure(result.error)
        }

        val deleteResult = DeleteFileStep.process(input.second)

        if (deleteResult is Result.Failure) {
            return Result.failure(deleteResult.error)
        }

        return Result.success(
            ServerData(
                version = input.first,
                items = result.getOrNull() ?: emptyList()
            )
        )
    }
}

data object DeleteFileStep : Step<Path, GetServerFilesFailure, Unit> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: Path): Result<GetServerFilesFailure, Unit> {
        return try {
            logger.info("Deleting temporary directory: {}", input)
            input.toFile().deleteRecursively()
            Result.success()
        } catch (e: Exception) {
            logger.error("Failed to delete temporary directory: {}", input, e)
            Result.failure(GetServerFilesFailure.FileError(this.javaClass))
        }
    }
}

data object ExtractItemsDataStep : Step<Pair<MinecraftVersion.Release, Path>, GetServerFilesFailure, List<Item>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val keyWhiteList = listOf(
        "item.minecraft.",
        "block.minecraft."
    )

    private val keyBlackList = listOf(
        "desc",
        "applies_to",
        "block.minecraft.banner.",
        "banner_pattern",
        "item.minecraft.shield.",
        "item.minecraft.firework_star.",
        "block.minecraft.spawn.not_valid",
        "block.minecraft.tnt.disabled",
        "item.minecraft.bundle.",
        "item.minecraft.crossbow.projectile.",
        "item.minecraft.debug_stick.",
        "item.minecraft.firework_rocket.",
        "item.minecraft.smithing_template."
    )

    private val valueBlackList = listOf(
        "%"
    )

    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<GetServerFilesFailure, List<Item>> {
        try {
            // input.second is the directory, we need to read lang/en_us.json from within it
            val enUsFile = input.second.resolve("lang").resolve("en_us.json")
            logger.info("Reading items from: {}", enUsFile)

            val content = enUsFile.toFile().readText()

            val map = Json.decodeFromString<LinkedHashMap<String, String>>(content)
                .filterKeys { key -> keyWhiteList.any { whiteListItem -> key.startsWith(whiteListItem) } }
                .filterKeys { key -> keyBlackList.none { blackListItem -> key.contains(blackListItem) } }
                .filterValues { value -> valueBlackList.none { blackListItem -> value.contains(blackListItem) } }
                .map { (key, value) -> Item(key, value) }

            logger.info("Extracted {} items for version {}", map.size, input.first)
            return Result.success<GetServerFilesFailure, List<Item>>(map)
        } catch (e: Exception) {
            logger.error("Failed to extract items for version {}: {}", input.first, e.message, e)
            return Result.failure(GetServerFilesFailure.FileError(this.javaClass))
        }
    }
}