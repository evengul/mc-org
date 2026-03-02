package app.mcorg.data.minecraft.extract

import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

data class ExtractRelevantMinecraftFilesStep(
    val dirCreator: (String) -> Path = { versionString -> Files.createTempDirectory("minecraft-server-$versionString") }
) : Step<Pair<MinecraftVersion.Release, InputStream>, ExtractionFailure, Pair<MinecraftVersion.Release, Path>> {
    private val logger = LoggerFactory.getLogger(ExtractRelevantMinecraftFilesStep::class.java)

    override suspend fun process(input: Pair<MinecraftVersion.Release, InputStream>): Result<ExtractionFailure, Pair<MinecraftVersion.Release, Path>> {
        val (version, serverInputStream) = input
        val versionString = "${version.major}.${version.minor}${if (version.patch != 0) ".${version.patch}" else ""}"
        val innerJarPath = "META-INF/versions/${versionString}/"

        try {
            // Create temp directory for extracted files
            val outputDir = withContext(Dispatchers.IO) {
                dirCreator(versionString)
            }

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
            val hasExtractedFiles = withContext(Dispatchers.IO) {
                Files.walk(outputDir)
            }.anyMatch { Files.isRegularFile(it) }

            if (!hasExtractedFiles) {
                logger.error("No relevant files were extracted for version $versionString from $serverInputStream")
                return Result.failure(ExtractionFailure.FileExtractionFailed(version, "No relevant files were extracted for version $versionString"))
            }

            logger.info("Successfully extracted server files for version $versionString to $outputDir")
            return Result.success(version to outputDir)
        } catch (e: Exception) {
            logger.error("Failed to extract server files for version $versionString: ${e.message}", e)
            return Result.failure(ExtractionFailure.FileExtractionFailed(version, "Failed to extract server files: ${e.message}"))
        }
    }

    /**
     * Determine if the given entry should be extracted.
     *
     * Other entries we might want later:
     * entryName == "assets/minecraft/lang/deprecated.json" ||
     * entryName.startsWith("data/minecraft/dimension_type/") ||
     * entryName.startsWith("data/minecraft/structure/") ||
     * entryName.startsWith("data/minecraft/worldgen/")
     */
    private fun shouldExtract(entryName: String): Boolean {
        return entryName == "assets/minecraft/lang/en_us.json" ||
                entryName.startsWith("data/minecraft/tags/block/") ||
                entryName.startsWith("data/minecraft/tags/item/") ||
                entryName.startsWith("data/minecraft/tags/items/") ||       // pre-1.21
                entryName.startsWith("data/minecraft/tags/blocks/") ||      // pre-1.21
                entryName.startsWith("data/minecraft/recipe/") ||
                entryName.startsWith("data/minecraft/recipes/") ||          // pre-1.21
                entryName.startsWith("data/minecraft/loot_table/") ||
                entryName.startsWith("data/minecraft/loot_tables/")         // pre-1.21
    }

    private fun extractEntry(zipStream: ZipInputStream, entryName: String, outputDir: Path) {
        // Determine target path (remove assets/minecraft or data/minecraft prefix)
        val targetPath = when {
            entryName.startsWith("assets/minecraft/lang/") -> {
                outputDir.resolve("lang").resolve(entryName.substringAfter("assets/minecraft/lang/"))
            }
            entryName.startsWith("data/minecraft/") -> {
                outputDir.resolve(entryName.substringAfter("data/minecraft/"))
            }
            else -> return
        }

        // Prevent Zip Slip: ensure the resolved path stays within the output directory
        val normalizedTarget = targetPath.normalize()
        if (!normalizedTarget.startsWith(outputDir.normalize())) {
            logger.warn("Skipping zip entry with path traversal: {}", entryName)
            return
        }

        // Create parent directories
        Files.createDirectories(normalizedTarget.parent)

        // Copy file content
        Files.copy(zipStream, normalizedTarget, StandardCopyOption.REPLACE_EXISTING)
        logger.debug("Extracted: {} -> {}", entryName, normalizedTarget)
    }
}
