package app.mcorg.pipeline.minecraft

import app.mcorg.config.GithubGistsApiConfig
import app.mcorg.config.NoBaseUrlApiConfig
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

sealed interface GetServerFilesFailure {
    data class ApiFailure<S : Step<*, *, *>>(val source: Class<S>) : GetServerFilesFailure
    data class FileError<S : Step<*, *, *>>(val source: Class<S>) : GetServerFilesFailure
    data class DatabaseError<S : Step<*, *, *>>(val source: Class<S>) : GetServerFilesFailure
    data class MultipleErrors(val errors: List<GetServerFilesFailure>) : GetServerFilesFailure
}

val serverFilesPipeline = Pipeline.create<GetServerFilesFailure, Unit>()
    .pipe(object : Step<Unit, GetServerFilesFailure, List<MinecraftVersion.Release>> {
        override suspend fun process(input: Unit): Result<GetServerFilesFailure, List<MinecraftVersion.Release>> {
            return GetAvailableVersionsStep.process(input)
                .mapError { GetServerFilesFailure.ApiFailure(GetAvailableVersionsStep.javaClass) }
        }
    })
    .pipe(GetServerUrlsStep)
    .pipe(FilterAlreadyStoredVersionsStep)
    .pipe(ProcessServerFilesStep)

private data object GetServerUrlsStep : Step<List<MinecraftVersion.Release>, GetServerFilesFailure, List<Pair<MinecraftVersion.Release, URI>>> {
    override suspend fun process(input: List<MinecraftVersion.Release>): Result<GetServerFilesFailure, List<Pair<MinecraftVersion.Release, URI>>> {
        val logger = LoggerFactory.getLogger(this.javaClass)
        return GithubGistsApiConfig.getProvider().getRaw<Unit, GetServerFilesFailure>(
            url = GithubGistsApiConfig.getServerJarsUrl(),
            errorMapper = { GetServerFilesFailure.ApiFailure(this.javaClass) }
        ).process(Unit).map {
            val lines = it.bufferedReader(Charsets.UTF_8).use { reader -> reader.readLines() }
                .map { line ->
                val parts = line.split("|")
                    .filter { part -> part.isNotBlank() }
                    .map { part -> part.trim() }
                parts[0] to parts[1]
            }

            lines.filter { (version, _) -> !version.contains("Minecraft Version") }
                .filter { (version, _) -> !version.contains("---------") }
                .filter { (version, url) -> version.isNotBlank() && url.isNotBlank() }
                .filter { (version, _) -> version.matches("""1\.\d+(\.\d+)?""".toRegex()) }
                .filter { (_, url) -> url.endsWith("server.jar") }
                .mapNotNull { (version, url) ->
                    try {
                        MinecraftVersion.Release.fromString(version) to URI.create(url)
                    } catch (e: IllegalArgumentException) {
                        logger.error("Invalid version format or URL in gist: $version", e)
                        null
                    }
                }.filter { result -> input.contains(result.first) }
        }
    }
}

private data object FilterAlreadyStoredVersionsStep : Step<List<Pair<MinecraftVersion.Release, URI>>, GetServerFilesFailure, List<Pair<MinecraftVersion.Release, URI>>> {
    override suspend fun process(input: List<Pair<MinecraftVersion.Release, URI>>): Result<GetServerFilesFailure, List<Pair<MinecraftVersion.Release, URI>>> {
        val logger = LoggerFactory.getLogger(this.javaClass)
        val storedVersions = GetSupportedVersionsStep.process(Unit)

        if (storedVersions is Result.Failure) {
            return Result.failure(GetServerFilesFailure.DatabaseError(this.javaClass))
        }

        return Result.success(
            input.filter {
                val allMigrationsComplete = AllFileMigrationsCompletedStep.process(it.first)

                if (allMigrationsComplete is Result.Failure) {
                    logger.error("Failed to check migrations for version ${it.first}")
                    return Result.failure(GetServerFilesFailure.DatabaseError(this.javaClass))
                } else if (allMigrationsComplete.getOrNull() == true) {
                    logger.info("All migrations complete for version ${it.first}, skipping download.")
                    false
                } else {
                    logger.info("Migrations not complete for version ${it.first}, will download and process.")
                    true
                }
            }
        )
    }
}

private data object AllFileMigrationsCompletedStep : Step<MinecraftVersion.Release, GetServerFilesFailure, Boolean> {
    override suspend fun process(input: MinecraftVersion.Release): Result<GetServerFilesFailure, Boolean> {
        return DatabaseSteps.query<MinecraftVersion.Release, GetServerFilesFailure, Boolean>(
            sql = SafeSQL.select("""
                SELECT EXISTS(SELECT 1 FROM minecraft_items WHERE version = ?) 
                    AND EXISTS(SELECT 1 FROM minecraft_version WHERE version = ?)
            """.trimIndent()),
            errorMapper = { GetServerFilesFailure.DatabaseError(this.javaClass) },
            parameterSetter = { statement, version ->
                statement.setString(1, version.toString())
                statement.setString(2, version.toString())
            },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    resultSet.getBoolean(1)
                } else {
                    false
                }
            }
        ).process(input)
    }
}

private data object ProcessServerFilesStep : Step<List<Pair<MinecraftVersion.Release, URI>>, GetServerFilesFailure, Unit> {
    override suspend fun process(input: List<Pair<MinecraftVersion.Release, URI>>): Result<GetServerFilesFailure, Unit> {
        val getServerFilePipeline = Pipeline.create<GetServerFilesFailure, Pair<MinecraftVersion.Release, URI>>()
            .pipe(GetServerFileStep)
            .pipe(StoreServerFileStep)
            .pipe(ExtractRelevantMinecraftFilesStep)
            .pipe(ExtractMinecraftDataStep)
            .pipe(StoreMinecraftDataStep)

        val result = input.map { getServerFilePipeline.execute(it) }

        val errors = result.filterIsInstance<Result.Failure<GetServerFilesFailure>>().map { it.error }

        if (errors.isNotEmpty()) {
            return Result.failure(GetServerFilesFailure.MultipleErrors(errors))
        }

        return Result.success()
    }
}

private data object GetServerFileStep : Step<Pair<MinecraftVersion.Release, URI>, GetServerFilesFailure, Pair<MinecraftVersion.Release, InputStream>> {
    override suspend fun process(input: Pair<MinecraftVersion.Release, URI>): Result<GetServerFilesFailure, Pair<MinecraftVersion.Release, InputStream>> {
        return NoBaseUrlApiConfig.getProvider().getRaw<Pair<MinecraftVersion.Release, URI>, GetServerFilesFailure>(
            url = input.second.toString(),
            errorMapper = { _ -> GetServerFilesFailure.ApiFailure(this.javaClass) }
        ).process(input).map { input.first to it }
    }
}

private data object StoreServerFileStep : Step<Pair<MinecraftVersion.Release, InputStream>, GetServerFilesFailure, Pair<MinecraftVersion.Release, Path>> {
    private val logger = LoggerFactory.getLogger(StoreServerFileStep::class.java)
    override suspend fun process(input: Pair<MinecraftVersion.Release, InputStream>): Result<GetServerFilesFailure, Pair<MinecraftVersion.Release, Path>> {
        try {
            val path = Files.createTempFile("server-${input.first}", ".jar")
            input.second.use { inputStream -> Files.copy(inputStream, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            return Result.success(input.first to path)
        } catch (e: Exception) {
            logger.error("Failed to store server file for version ${input.first}: ${e.message}", e)
            return Result.failure(GetServerFilesFailure.FileError(this.javaClass))
        }
    }
}

private data object ExtractRelevantMinecraftFilesStep : Step<Pair<MinecraftVersion.Release, Path>, GetServerFilesFailure, Pair<MinecraftVersion.Release, Path>> {
    private val logger = LoggerFactory.getLogger(ExtractRelevantMinecraftFilesStep::class.java)

    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<GetServerFilesFailure, Pair<MinecraftVersion.Release, Path>> {
        val (version, serverJarPath) = input
        val versionString = version.toString().replace(".0", "")
        val innerJarPath = "META-INF/versions/${versionString}/"

        try {
            // Create temp directory for extracted files
            val outputDir = Files.createTempDirectory("minecraft-server-$versionString")

            // Open outer JAR
            FileInputStream(serverJarPath.toFile()).use { outerFileStream ->
                ZipInputStream(outerFileStream).use { outerZip ->
                    var outerEntry = outerZip.nextEntry

                    // Find the inner JAR entry
                    while (outerEntry != null) {
                        if (outerEntry.name.startsWith(innerJarPath) && !outerEntry.isDirectory) {
                            logger.info("Found inner JAR at: ${outerEntry.name}")

                            // Create nested ZipInputStream from the inner JAR entry
                            ZipInputStream(outerZip).use { innerZip ->
                                var innerEntry = innerZip.nextEntry

                                // Extract matching files from inner JAR
                                while (innerEntry != null) {
                                    val entryName = innerEntry.name

                                    if (!innerEntry.isDirectory && shouldExtract(entryName)) {
                                        extractEntry(innerZip, entryName, outputDir)
                                    }

                                    innerEntry = innerZip.nextEntry
                                }
                            }
                            break
                        }
                        outerEntry = outerZip.nextEntry
                    }
                }
            }

            logger.info("Successfully extracted server files for version $versionString to $outputDir")
            return Result.success(version to outputDir)
        } catch (e: Exception) {
            logger.error("Failed to extract server files for version $versionString: ${e.message}", e)
            return Result.failure(GetServerFilesFailure.FileError(this.javaClass))
        } finally {
            try {
                logger.info("Deleting temporary server JAR file at $serverJarPath")
                Files.deleteIfExists(serverJarPath)
            } catch (e: Exception) {
                logger.warn("Failed to delete temporary server JAR file at $serverJarPath: ${e.message}", e)
            }
        }
    }

    /**
     * Determine if the given entry should be extracted.
     *
     * Other entries we might want later:
     * entryName == "assets/minecraft/lang/deprecated.json" ||
     * entryName.startsWith("data/minecraft/dimension_type/") ||
     * entryName.startsWith("data/minecraft/loot_table/") ||
     * entryName.startsWith("data/minecraft/recipe/") ||
     * entryName.startsWith("data/minecraft/structure/") ||
     * entryName.startsWith("data/minecraft/worldgen/")
     */
    private fun shouldExtract(entryName: String): Boolean {
        return entryName == "assets/minecraft/lang/en_us.json"
    }

    private fun extractEntry(zipStream: ZipInputStream, entryName: String, outputDir: Path) {
        // Determine target path (remove assets/minecraft or data/minecraft prefix)
        val targetPath = when {
            entryName.startsWith("assets/minecraft/lang/") -> {
                outputDir.resolve("lang").resolve(entryName.substringAfter("assets/minecraft/lang/"))
            }
            entryName.startsWith("data/minecraft/") -> {
                outputDir.resolve("data").resolve(entryName.substringAfter("data/minecraft/"))
            }
            else -> return
        }

        // Create parent directories
        Files.createDirectories(targetPath.parent)

        // Copy file content
        Files.copy(zipStream, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        logger.debug("Extracted: {} -> {}", entryName, targetPath)
    }
}