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
import kotlin.time.Duration.Companion.milliseconds

object ServerFileDownloader {
    private val logger = LoggerFactory.getLogger(ServerFileDownloader::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private const val MOJANG_VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
    private val EARLIEST_SUPPORTED_VERSION = MinecraftVersion.Release(1, 18, 0)

    suspend fun downloadAndExtract(outputDir: Path) {
        val urls = getServerUrls()

        if (urls.isEmpty()) {
            logger.warn("No server URLs found to download")
            return
        }

        for ((version, uri) in urls) {
            val versionString = version.toString().replace(".0", "")
            val versionDir = outputDir.resolve(versionString)

            logger.info("Downloading and extracting server files for version $version")
            try {
                val inputStream = withContext(Dispatchers.IO) {
                    uri.toURL().openStream()
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

    /**
     * Fetches the Mojang version manifest, filters to releases at or above
     * [EARLIEST_SUPPORTED_VERSION], and resolves each version's server.jar URL by fetching its
     * per-version metadata JSON.
     */
    private suspend fun getServerUrls(): List<Pair<MinecraftVersion.Release, URI>> {
        val manifestJson = withContext(Dispatchers.IO) {
            URI.create(MOJANG_VERSION_MANIFEST_URL).toURL().readText()
        }

        val entries = json.parseToJsonElement(manifestJson)
            .jsonObject["versions"]
            ?.jsonArray
            ?: return emptyList()

        return entries
            .mapNotNull { element ->
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
            .mapNotNull { (version, metaUrl) ->
                val serverUrl = fetchServerUrl(metaUrl)
                if (serverUrl == null) {
                    logger.warn("No server download for $version, skipping")
                    null
                } else {
                    version to URI.create(serverUrl)
                }
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
