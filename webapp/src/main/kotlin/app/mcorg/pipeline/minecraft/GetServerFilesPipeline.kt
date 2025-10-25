package app.mcorg.pipeline.minecraft

import app.mcorg.config.GithubGistsApiConfig
import app.mcorg.config.NoBaseUrlApiConfig
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

sealed interface GetServerFilesFailure {
    object ApiFailure : GetServerFilesFailure
    object FileError : GetServerFilesFailure
    object DatabaseError : GetServerFilesFailure
}

val serverFilesPipeline = Pipeline.create<GetServerFilesFailure, Unit>()
    .pipe(object : Step<Unit, GetServerFilesFailure.ApiFailure, List<MinecraftVersion.Release>> {
        override suspend fun process(input: Unit): Result<GetServerFilesFailure.ApiFailure, List<MinecraftVersion.Release>> {
            return GetAvailableVersionsStep.process(input)
                .mapError { GetServerFilesFailure.ApiFailure }
        }
    })
    .pipe(GetServerUrlsStep)
    .pipe(FilterAlreadyStoredVersionsStep)
    .pipe(GetServerFilesStep)
    .pipe(ExtractAllServersDataStep)
    .pipe(StoreAllServerDataStep)

private data object GetServerUrlsStep : Step<List<MinecraftVersion.Release>, GetServerFilesFailure, List<Pair<MinecraftVersion.Release, URI>>> {
    override suspend fun process(input: List<MinecraftVersion.Release>): Result<GetServerFilesFailure, List<Pair<MinecraftVersion.Release, URI>>> {
        val logger = LoggerFactory.getLogger(GetServerUrlsStep::class.java)
        return GithubGistsApiConfig.getProvider().getRaw<Unit, GetServerFilesFailure.ApiFailure>(
            url = GithubGistsApiConfig.getServerJarsUrl(),
            errorMapper = { GetServerFilesFailure.ApiFailure }
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
        val logger = LoggerFactory.getLogger(FilterAlreadyStoredVersionsStep::class.java)
        val storedVersions = GetSupportedVersionsStep.process(Unit)

        if (storedVersions is Result.Failure) {
            return Result.failure(GetServerFilesFailure.DatabaseError)
        }

        return Result.success(
            input.filter {
                val isStored = storedVersions.getOrNull()!!.contains(it.first)
                if (isStored) {
                    logger.info("Version ${it.first} already stored, skipping download.")
                }
                !isStored
            }
        )
    }
}

private data object GetServerFilesStep : Step<List<Pair<MinecraftVersion.Release, URI>>, GetServerFilesFailure, List<Pair<MinecraftVersion.Release, Path>>> {
    override suspend fun process(input: List<Pair<MinecraftVersion.Release, URI>>): Result<GetServerFilesFailure, List<Pair<MinecraftVersion.Release, Path>>> {
        val getServerFilePipeline = Pipeline.create<GetServerFilesFailure, Pair<MinecraftVersion.Release, URI>>()
            .pipe(GetServerFileStep)
            .pipe(StoreServerFileStep)
            .pipe(ExtractActualServerFileStep)
            .map { listOf(it) }

        val results = mutableListOf<Pair<MinecraftVersion.Release, Path>>()

        input.forEach { release ->
            when (val result = getServerFilePipeline.execute(release)) {
                is Result.Success -> results.addAll(result.value)
                is Result.Failure -> return Result.failure(result.error)
            }
        }

        return Result.success(results)
    }
}

private data object GetServerFileStep : Step<Pair<MinecraftVersion.Release, URI>, GetServerFilesFailure, Pair<MinecraftVersion.Release, InputStream>> {
    override suspend fun process(input: Pair<MinecraftVersion.Release, URI>): Result<GetServerFilesFailure, Pair<MinecraftVersion.Release, InputStream>> {
        return NoBaseUrlApiConfig.getProvider().getRaw<Pair<MinecraftVersion.Release, URI>, GetServerFilesFailure>(
            url = input.second.toString(),
            errorMapper = { _ -> GetServerFilesFailure.ApiFailure }
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
            return Result.failure(GetServerFilesFailure.FileError)
        }
    }
}

private data object ExtractActualServerFileStep : Step<Pair<MinecraftVersion.Release, Path>, GetServerFilesFailure, Pair<MinecraftVersion.Release, Path>> {
    private val logger = LoggerFactory.getLogger(ExtractActualServerFileStep::class.java)

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
            return Result.failure(GetServerFilesFailure.FileError)
        }
    }

    private fun shouldExtract(entryName: String): Boolean {
        return entryName == "assets/minecraft/lang/en_us.json" ||
                entryName == "assets/minecraft/lang/deprecated.json" ||
                entryName.startsWith("data/minecraft/dimension_type/") ||
                entryName.startsWith("data/minecraft/loot_table/") ||
                entryName.startsWith("data/minecraft/recipe/") ||
                entryName.startsWith("data/minecraft/structure/") ||
                entryName.startsWith("data/minecraft/worldgen/")
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