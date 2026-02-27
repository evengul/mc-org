package app.mcorg.data.minecraft

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.ServerData
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.data.minecraft.extract.ExtractItemsStep
import app.mcorg.data.minecraft.extract.ExtractResourceSources
import app.mcorg.data.minecraft.failure.ExtractionFailure
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path

data object ExtractMinecraftDataStep : Step<Pair<MinecraftVersion.Release, Path>, ExtractionFailure, ServerData> {
    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<ExtractionFailure, ServerData> {
        try {
            val items = ExtractItemsStep.process(input)
            val itemSources = ExtractResourceSources.process(input)

            if (items is Result.Failure) {
                return items
            }

            if (itemSources is Result.Failure) {
                return itemSources
            }

            val allItems = items.getOrThrow() + itemSources.getOrThrow().second.flatMap {
                source -> source.requiredItems.map { it.first } + source.producedItems.map { it.first }
            }.distinctBy { it.id }

            return Result.success(
                ServerData(
                    version = input.first,
                    items = allItems,
                    sources = itemSources.getOrThrow().second
                )
            )
        } finally {
            DeleteFileStep.process(input.second)
        }
    }
}

data object DeleteFileStep : Step<Path, ExtractionFailure, Unit> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: Path): Result<ExtractionFailure, Unit> {
        return try {
            logger.info("Deleting temporary directory: {}", input)
            deleteWithRetry(input)
            Result.success()
        } catch (e: Exception) {
            logger.error("Failed to delete temporary directory: {}", input, e)
            Result.failure(ExtractionFailure.ServerFileDeletionFailed)
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
