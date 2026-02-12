package app.mcorg.pipeline.minecraft.extract

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

abstract class ParseFilesRecursivelyStep<T> : Step<Pair<MinecraftVersion.Release, Path>, AppFailure, List<T>> {
    internal lateinit var version: MinecraftVersion.Release
    internal lateinit var basePath: Path

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<AppFailure, List<T>> {
        basePath = input.second
        version = input.first

        val paths = extractFilePaths()

        val parsedResults = coroutineScope {
            (paths.getOrNull() ?: emptyList()).map { path ->
                async(Dispatchers.IO) {
                    when (val contentResult = parseFile(Files.readString(path), getRelativePath(path))) {
                        is Result.Success -> contentResult
                        is Result.Failure -> {
                            logger.error("Error parsing file at $path for version $version: ${contentResult.error}")
                            contentResult
                        }
                    }
                }
            }.awaitAll()
        }

        if (parsedResults.any { it is Result.Failure<AppFailure> }) {
            return Result.Failure(
                AppFailure.FileError(
                    this.javaClass,
                    parsedResults.asSequence().filter { it is Result.Failure<*> }
                        .map { it as Result.Failure }.map { it.error as AppFailure.FileError }
                        .mapNotNull { it.filename?.takeIf { filename -> filename.isNotEmpty() } ?: it.source.simpleName }.joinToString { ", " })
            )
        }

        val results = parsedResults.mapNotNull { (it as? Result.Success)?.value }

        return Result.Success(results)
    }

    private fun getRelativePath(file: Path): String {
        return basePath.relativize(file)
            .toString()
            .replace("\\", "/") // Normalize Windows paths to use forward slashes
    }

    fun extractFilePaths(): Result<AppFailure, List<Path>> {
        return try {
            if (!basePath.toFile().exists()) {
                logger.warn("File does not exist: $basePath in version $version")
                return Result.Failure(AppFailure.FileError(this.javaClass, basePath.toString()))
            }
            val filePaths = Files.walk(basePath).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) }
                    .filter { includePath(it) }
                    .toList()
            }
            Result.Success(filePaths)
        } catch (e: Exception) {
            logger.error("Error extracting file paths for version $version at $basePath", e)
            Result.Failure(AppFailure.FileError(this.javaClass))
        }
    }

    fun includePath(file: Path): Boolean {
        return file.toFile().extension == "json"
    }

    abstract suspend fun parseFile(content: String, filename: String): Result<AppFailure, T>
}