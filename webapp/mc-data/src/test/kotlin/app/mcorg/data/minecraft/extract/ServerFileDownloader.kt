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

    private const val FABRIC_MC_VERSIONS_URL = "https://meta.fabricmc.net/v2/versions/game"
    private const val SERVER_JARS_GIST_URL = "https://gist.githubusercontent.com/cliffano/77a982a7503669c3e1acb0a0cf6127e9/raw/e91cfeacc56e461d5943e100a2bc7eb0919c0a83/minecraft-server-jar-downloads.md"

    suspend fun downloadAndExtract(outputDir: Path) {
        val versions = getAvailableVersions()
        val urls = getServerUrls(versions)

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

    private suspend fun getAvailableVersions(): List<MinecraftVersion.Release> {
        val response = withContext(Dispatchers.IO) {
            URI.create(FABRIC_MC_VERSIONS_URL).toURL().readText()
        }

        return json.parseToJsonElement(response).jsonArray
            .filter { it.jsonObject["stable"]?.jsonPrimitive?.content == "true" }
            .mapNotNull { element ->
                val version = element.jsonObject["version"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (!version.matches("""1\.\d+(\.\d+)?""".toRegex())) return@mapNotNull null
                try {
                    MinecraftVersion.Release.fromString(version)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
            .filter { it.major == 1 && it.minor >= 18 }
    }

    private suspend fun getServerUrls(versions: List<MinecraftVersion.Release>): List<Pair<MinecraftVersion.Release, URI>> {
        val response = withContext(Dispatchers.IO) {
            URI.create(SERVER_JARS_GIST_URL).toURL().readText()
        }

        return response.lines()
            .mapNotNull { line ->
                val parts = line.split("|").filter { it.isNotBlank() }.map { it.trim() }
                if (parts.size >= 2) parts[0] to parts[1] else null
            }
            .filter { (version, _) -> !version.contains("Minecraft Version") }
            .filter { (version, _) -> !version.contains("---------") }
            .filter { (version, url) -> version.isNotBlank() && url.isNotBlank() }
            .filter { (version, _) -> version.matches("""1\.\d+(\.\d+)?""".toRegex()) }
            .filter { (_, url) -> url.endsWith("server.jar") }
            .mapNotNull { (version, url) ->
                try {
                    MinecraftVersion.Release.fromString(version) to URI.create(url)
                } catch (e: IllegalArgumentException) {
                    logger.error("Invalid version format or URL: $version", e)
                    null
                }
            }
            .filter { (version, _) -> versions.contains(version) }
    }
}
