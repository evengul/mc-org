package app.mcorg.pipeline.minecraft.extract

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

abstract class ParseFilesRecursivelyStep<T> : Step<Pair<MinecraftVersion.Release, Path>, AppFailure, List<T>> {
    internal lateinit var version: MinecraftVersion.Release
    internal lateinit var path: Path

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, List<T>> {
        path = input.second
        version = input.first

        val paths = extractFilePaths()

        val parsedResults = coroutineScope {
            (paths.getOrNull() ?: emptyList()).map { path ->
                async(Dispatchers.IO) {
                    when (val contentResult = parseFile(Files.readString(path), path.toFile().name)) {
                        is Result.Success -> contentResult.value
                        is Result.Failure -> {
                            logger.error("Error parsing file at $path for version $version: ${contentResult.error}")
                            emptyList()
                        }
                    }
                }
            }.flatMap { it.await() }
        }

        return Result.Success(parsedResults)
    }

    fun extractFilePaths(): Result<AppFailure, List<Path>> {
        return try {
            if (!path.toFile().exists()) {
                logger.warn("File does not exist: $path in version $version")
                return Result.Success(emptyList())
            }
            val filePaths = Files.walk(path).use { paths ->
                paths.filter { Files.isRegularFile(it) }.toList()
            }
            Result.Success(filePaths)
        } catch (e: Exception) {
            logger.error("Error extracting file paths for version $version at $path", e)
            Result.Failure(AppFailure.FileError(this.javaClass))
        }
    }

    abstract suspend fun parseFile(content: String, filename: String): Result<AppFailure, List<T>>
}