package app.mcorg.data.minecraft.extract

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration.Companion.milliseconds

object ServerFileDownloader {
    private val logger = LoggerFactory.getLogger(ServerFileDownloader::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private const val MOJANG_VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
    private val EARLIEST_SUPPORTED_VERSION = MinecraftVersion.Release(1, 18, 0)

    /**
     * Idempotent: fetches the Mojang manifest, diffs the list of supported releases against what's
     * already extracted under [outputDir], and only downloads + extracts versions that are missing.
     * Safe to call repeatedly — e.g. every CI run, so new Mojang releases get picked up without
     * wiping the cache.
     */
    suspend fun downloadAndExtract(outputDir: Path) {
        val manifestEntries = fetchSupportedReleaseEntries()
        if (manifestEntries.isEmpty()) {
            logger.warn("No supported releases found in Mojang manifest")
            return
        }

        val missing = manifestEntries.filter { (version, _) -> !isAlreadyExtracted(outputDir, version) }

        if (missing.isEmpty()) {
            logger.info("All ${manifestEntries.size} supported versions already extracted at $outputDir — nothing to do")
            return
        }

        logger.info(
            "Found ${missing.size} missing version(s) to download: ${missing.joinToString(", ") { it.first.toString() }} " +
                    "(skipping ${manifestEntries.size - missing.size} already extracted)"
        )

        for ((version, metaUrl) in missing) {
            val serverUrl = fetchServerUrl(metaUrl)
            if (serverUrl == null) {
                logger.warn("No server.jar download for $version, skipping")
                continue
            }

            val versionDir = outputDir.resolve(versionDirName(version))
            logger.info("Downloading and extracting server files for version $version")
            try {
                val inputStream = withContext(Dispatchers.IO) {
                    URI.create(serverUrl).toURL().openStream()
                }

                val result = ExtractRelevantMinecraftFilesStep(
                    dirCreator = { versionDir.createDirectories() }
                ).process(version to inputStream)

                when (result) {
                    is Result.Success -> logger.info("Successfully extracted version $version")
                    is Result.Failure -> logger.error("Failed to extract version $version: ${result.error}")
                }

                delay(500.milliseconds)
            } catch (e: Exception) {
                logger.error("Failed to download/extract version $version: ${e.message}", e)
            }
        }
    }

    private fun isAlreadyExtracted(outputDir: Path, version: MinecraftVersion.Release): Boolean {
        val dir = outputDir.resolve(versionDirName(version))
        return dir.exists() && dir.listDirectoryEntries().isNotEmpty()
    }

    private fun versionDirName(version: MinecraftVersion.Release): String =
        version.toString().replace(".0", "")

    /**
     * Fetches the Mojang version manifest and returns `(version, per-version metadata URL)` pairs
     * for every release at or above [EARLIEST_SUPPORTED_VERSION]. Does NOT fetch per-version
     * metadata — that's done lazily in [downloadAndExtract] only for versions we actually need.
     */
    private suspend fun fetchSupportedReleaseEntries(): List<Pair<MinecraftVersion.Release, String>> {
        val manifestJson = withContext(Dispatchers.IO) {
            URI.create(MOJANG_VERSION_MANIFEST_URL).toURL().readText()
        }

        val entries = json.parseToJsonElement(manifestJson)
            .jsonObject["versions"]
            ?.jsonArray
            ?: return emptyList()

        return entries.mapNotNull { element ->
            val obj = element.jsonObject
            if (obj["type"]?.jsonPrimitive?.content != "release") return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val version = try {
                MinecraftVersion.Release.fromString(id)
            } catch (e: IllegalArgumentException) {
                return@mapNotNull null
            }
            if (version < EARLIEST_SUPPORTED_VERSION) return@mapNotNull null
            version to url
        }
    }

    private suspend fun fetchServerUrl(metaUrl: String): String? {
        return try {
            val metaJson = withContext(Dispatchers.IO) {
                URI.create(metaUrl).toURL().readText()
            }
            json.parseToJsonElement(metaJson)
                .jsonObject["downloads"]
                ?.jsonObject?.get("server")
                ?.jsonObject?.get("url")
                ?.jsonPrimitive?.content
        } catch (e: Exception) {
            logger.error("Failed to fetch per-version metadata at $metaUrl: ${e.message}", e)
            null
        }
    }
}
