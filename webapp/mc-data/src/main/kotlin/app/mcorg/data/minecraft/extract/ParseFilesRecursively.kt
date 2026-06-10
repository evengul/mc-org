package app.mcorg.data.minecraft.extract

import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

private val logger = LoggerFactory.getLogger("app.mcorg.data.minecraft.extract.ParseFilesRecursively")

/**
 * Walks [basePath] recursively, parsing every `.json` file concurrently with [parseFile]
 * (which receives the file content and its base-relative path). Fails with
 * [ExtractionFailure.Multiple] if any file fails — extraction is all-or-nothing by design.
 */
suspend fun <T> parseJsonFilesRecursively(
    version: MinecraftVersion.Release,
    basePath: Path,
    parseFile: suspend (content: String, filename: String) -> Result<ExtractionFailure, T>,
): Result<ExtractionFailure, List<T>> {
    val paths = extractFilePaths(version, basePath)

    val parsedResults = coroutineScope {
        (paths.getOrNull() ?: emptyList()).map { path ->
            async(Dispatchers.IO) {
                when (val contentResult = parseFile(Files.readString(path), getRelativePath(basePath, path))) {
                    is Result.Success -> contentResult
                    is Result.Failure -> {
                        logger.error("Error parsing file at $path for version $version: ${contentResult.error}")
                        contentResult
                    }
                }
            }
        }.awaitAll()
    }

    if (parsedResults.any { it is Result.Failure<ExtractionFailure> }) {
        return Result.Failure(
            ExtractionFailure.Multiple(
                parsedResults.filterIsInstance<Result.Failure<ExtractionFailure>>()
                    .map { it.error }
            )
        )
    }

    val results = parsedResults.mapNotNull { (it as? Result.Success)?.value }

    return Result.Success(results)
}

private fun getRelativePath(basePath: Path, file: Path): String {
    return basePath.relativize(file)
        .toString()
        .replace("\\", "/") // Normalize Windows paths to use forward slashes
}

private fun extractFilePaths(version: MinecraftVersion.Release, basePath: Path): Result<ExtractionFailure, List<Path>> {
    return try {
        if (!basePath.toFile().exists()) {
            logger.warn("File does not exist: $basePath in version $version")
            return Result.Failure(ExtractionFailure.BasePathNotFound(basePath, version))
        }
        val filePaths = Files.walk(basePath).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.toFile().extension == "json" }
                .toList()
        }
        Result.Success(filePaths)
    } catch (e: Exception) {
        logger.error("Error extracting file paths for version $version at $basePath", e)
        Result.Failure(ExtractionFailure.FilePathExtractionFailed(basePath, version))
    }
}
