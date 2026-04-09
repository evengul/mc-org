package app.mcorg.pipeline.minecraft

import app.mcorg.config.MojangLauncherMetaApiConfig
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraftfiles.VersionManifest
import org.slf4j.LoggerFactory

private val EARLIEST_SUPPORTED_VERSION = MinecraftVersion.Release(1, 18, 0)

data object GetAvailableVersionsStep : Step<Unit, AppFailure.ApiError, List<MinecraftVersion.Release>> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun process(input: Unit): Result<AppFailure.ApiError, List<MinecraftVersion.Release>> {
        return MojangLauncherMetaApiConfig.getProvider().get<Unit, VersionManifest>(
            url = MojangLauncherMetaApiConfig.getVersionManifestUrl(),
        ).process(input)
            .map { manifest ->
                manifest.versions
                    .filter { it.type == "release" }
                    .mapNotNull { entry ->
                        runCatching { MinecraftVersion.Release.fromString(entry.id) }
                            .onFailure { logger.warn("Skipping unparseable release id from Mojang manifest: ${entry.id}") }
                            .getOrNull()
                    }
                    .filter { it >= EARLIEST_SUPPORTED_VERSION }
                    .sortedWith { a, b -> a.compareTo(b) }
            }
    }
}
