package app.mcorg.pipeline.minecraft

import app.mcorg.config.GithubGistsApiConfig
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

val serverFilesPipeline = Pipeline.create<AppFailure, Unit>()
    .pipe(GetAvailableVersionsStep)
    .pipe(GetServerUrlsStep)
    .pipe(FilterAlreadyStoredVersionsStep)
    .pipe(ProcessServerFilesStep)

private data object GetServerUrlsStep : Step<List<MinecraftVersion.Release>, AppFailure, List<Pair<MinecraftVersion.Release, URI>>> {
    override suspend fun process(input: List<MinecraftVersion.Release>): Result<AppFailure, List<Pair<MinecraftVersion.Release, URI>>> {
        val logger = LoggerFactory.getLogger(this.javaClass)
        return GithubGistsApiConfig.getProvider().getRaw<Unit>(
            url = GithubGistsApiConfig.getServerJarsUrl(),
        ).process(Unit).map {
            val lines = it.bufferedReader(Charsets.UTF_8).use { reader -> reader.readLines() }
                .mapNotNull { line ->
                    val parts = line.split("|")
                        .filter { part -> part.isNotBlank() }
                        .map { part -> part.trim() }

                    if (parts.size >= 2) {
                        parts[0] to parts[1]
                    } else {
                        logger.warn("Skipping malformed line: $line")
                        null
                    }
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

private data object FilterAlreadyStoredVersionsStep : Step<List<Pair<MinecraftVersion.Release, URI>>, AppFailure, List<Pair<MinecraftVersion.Release, URI>>> {
    override suspend fun process(input: List<Pair<MinecraftVersion.Release, URI>>): Result<AppFailure, List<Pair<MinecraftVersion.Release, URI>>> {
        val logger = LoggerFactory.getLogger(this.javaClass)
        val storedVersions = GetSupportedVersionsStep.process(Unit)

        if (storedVersions is Result.Failure) {
            return storedVersions
        }

        val shouldMigrate = AllFileMigrationsCompletedStep.process(input.map { it.first })

        if (shouldMigrate is Result.Failure) {
            logger.error("Failed to check migrations for some versions.")
            return shouldMigrate
        }

        return Result.success(
            input.filter {
                val allMigrationsComplete = shouldMigrate.getOrNull()?.get(it.first) ?: false

                if (allMigrationsComplete) {
                    logger.info("All migrations complete for version ${it.first}, skipping download.")
                } else {
                    logger.info("Migrations not complete for version ${it.first}, will download and process.")
                }

                !allMigrationsComplete
            }
        )
    }
}

private data object AllFileMigrationsCompletedStep : Step<List<MinecraftVersion.Release>, AppFailure, Map<MinecraftVersion.Release, Boolean>> {
    override suspend fun process(input: List<MinecraftVersion.Release>): Result<AppFailure, Map<MinecraftVersion.Release, Boolean>> {
        val logger = LoggerFactory.getLogger(this.javaClass)
        val result = DatabaseSteps.query<Unit, Map<MinecraftVersion.Release, Boolean>>(
            sql = SafeSQL.select("""
                SELECT mv.version,
                       CASE
                           WHEN mi.version IS NULL THEN FALSE
                           ELSE TRUE
                       END AS items_migrated
                FROM minecraft_version mv
                LEFT JOIN minecraft_items mi ON mv.version = mi.version
            """.trimIndent()),
            resultMapper = { resultSet ->
                val migrationStatus = mutableMapOf<MinecraftVersion.Release, Boolean>()
                while (resultSet.next()) {
                    val versionString = resultSet.getString("version")
                    val itemsMigrated = resultSet.getBoolean("items_migrated")
                    try {
                        val version = MinecraftVersion.Release.fromString(versionString)
                        migrationStatus[version] = itemsMigrated
                    } catch (e: IllegalArgumentException) {
                        logger.error("Invalid version format in database: $versionString", e)
                    }
                }
                migrationStatus
            }
        ).process(Unit)

        return result as? Result.Failure
            ?: Result.success(
                input.associateWith { version ->
                    result.getOrNull()?.get(version) ?: false
                }
            )
    }
}

private data object ProcessServerFilesStep : Step<List<Pair<MinecraftVersion.Release, URI>>, AppFailure, Unit> {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    override suspend fun process(input: List<Pair<MinecraftVersion.Release, URI>>): Result<AppFailure, Unit> {
        val processServerFilePipeline = Pipeline.create<AppFailure, Pair<MinecraftVersion.Release, URI>>()
            .pipe(GetServerFileStep)
            .pipe(ExtractRelevantMinecraftFilesStep)
            .pipe(ExtractMinecraftDataStep)
            .pipe(StoreMinecraftDataStep)

        if (input.isEmpty()) {
            logger.info("No new server files to process.")
            return Result.success()
        }

        val result = input.map {
            MDC.put("minecraftVersion", it.first.toString())
            val result = processServerFilePipeline.execute(it)
            try {
                delay(500)
            } catch (e: Exception) {
                logger.warn("Delay interrupted: ${e.message}", e)
            }
            result
        }

        val errors = result.filterIsInstance<Result.Failure<AppFailure>>().map { it.error }.distinctBy { it.javaClass }

        if (errors.isNotEmpty()) {
            return Result.failure(errors.first())
        }

        return Result.success()
    }
}

private data object GetServerFileStep : Step<Pair<MinecraftVersion.Release, URI>, AppFailure, Pair<MinecraftVersion.Release, InputStream>> {
    private val logger = LoggerFactory.getLogger(GetServerFileStep::class.java)
    override suspend fun process(input: Pair<MinecraftVersion.Release, URI>): Result<AppFailure, Pair<MinecraftVersion.Release, InputStream>> {
        return try {
            Result.success(input.first to input.second.toURL().openStream())
        } catch (e: Exception) {
            logger.error("Failed to download server file for version ${input.first} from ${input.second}: ${e.message}", e)
            Result.failure(AppFailure.ApiError.UnknownError)
        }
    }
}

private data object ExtractRelevantMinecraftFilesStep : Step<Pair<MinecraftVersion.Release, InputStream>, AppFailure, Pair<MinecraftVersion.Release, Path>> {
    private val logger = LoggerFactory.getLogger(ExtractRelevantMinecraftFilesStep::class.java)

    override suspend fun process(input: Pair<MinecraftVersion.Release, InputStream>): Result<AppFailure, Pair<MinecraftVersion.Release, Path>> {
        val (version, serverInputStream) = input
        val versionString = "${version.major}.${version.minor}${if (version.patch != 0) ".${version.patch}" else ""}"
        val innerJarPath = "META-INF/versions/${versionString}/"

        try {
            // Create temp directory for extracted files
            val outputDir = Files.createTempDirectory("minecraft-server-$versionString")

            // Open outer JAR
            serverInputStream.use { outerFileStream ->
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

            // Check if the output directory has any files
            val hasExtractedFiles = Files.walk(outputDir).anyMatch { Files.isRegularFile(it) }
            if (!hasExtractedFiles) {
                logger.error("No relevant files were extracted for version $versionString from $serverInputStream")
                return Result.failure(AppFailure.FileError(this.javaClass))
            }

            logger.info("Successfully extracted server files for version $versionString to $outputDir")
            return Result.success(version to outputDir)
        } catch (e: Exception) {
            logger.error("Failed to extract server files for version $versionString: ${e.message}", e)
            return Result.failure(AppFailure.FileError(this.javaClass))
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