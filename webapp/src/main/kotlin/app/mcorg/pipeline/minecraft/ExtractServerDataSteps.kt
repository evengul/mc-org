package app.mcorg.pipeline.minecraft

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path

data object ExtractMinecraftDataStep : Step<Pair<MinecraftVersion.Release, Path>, AppFailure, ServerData> {
    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, ServerData> {
        try {
            val result = ExtractItemsDataStep.process(input)

            if (result is Result.Failure) {
                return result
            }

            return Result.success(
                ServerData(
                    version = input.first,
                    items = result.getOrNull() ?: emptyList()
                )
            )
        } finally {
            DeleteFileStep.process(input.second)
        }
    }
}

data object DeleteFileStep : Step<Path, AppFailure, Unit> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: Path): Result<AppFailure, Unit> {
        return try {
            logger.info("Deleting temporary directory: {}", input)
            deleteWithRetry(input)
            Result.success()
        } catch (e: Exception) {
            logger.error("Failed to delete temporary directory: {}", input, e)
            Result.failure(AppFailure.FileError(this.javaClass))
        }
    }

    private fun deleteWithRetry(input: Path, attempts: Int = 3) {
        repeat(attempts) { attempt ->
            try {
                input.toFile().deleteRecursively()
            } catch (e: IOException) {
                if (attempt == 2) throw e
                Thread.sleep(100) // Brief delay for file locks to release
            }
        }
    }

}

data object ExtractItemsDataStep : Step<Pair<MinecraftVersion.Release, Path>, AppFailure, List<Item>> {
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

    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, List<Item>> {
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
                .filterKeys { key -> keyBlackList.none { blackListItem -> key.contains(blackListItem) } }
                .filterValues { value -> valueBlackList.none { blackListItem -> value.contains(blackListItem) } }
                .map { (key, value) -> Item(key, value) }

            if (map.isEmpty()) {
                logger.warn("No items extracted from en_us.json for version {}", input.first)
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