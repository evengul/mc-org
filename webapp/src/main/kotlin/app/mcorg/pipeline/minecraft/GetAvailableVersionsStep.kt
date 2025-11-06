package app.mcorg.pipeline.minecraft

import app.mcorg.config.FabricMcApiConfig
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.serialization.Serializable

@Serializable
private data class FabricVersion(
    val version: String,
    val stable: Boolean
)

private val EARLIEST_SUPPORTED_VERSION = MinecraftVersion.Release(1, 18, 0)

data object GetAvailableVersionsStep : Step<Unit, AppFailure.ApiError, List<MinecraftVersion.Release>> {
    override suspend fun process(input: Unit): Result<AppFailure.ApiError, List<MinecraftVersion.Release>> {
        return FabricMcApiConfig.getProvider().get<Unit, Array<FabricVersion>>(
            url = FabricMcApiConfig.getVersionsUrl(),
        ).process(input)
            .map { versions ->
                versions.filter { version -> version.stable }
                    .map { version -> version.version }.toSet()
                    .map { if (it.count { c -> c == '.' } == 1) "$it.0" else it }
                    .map { MinecraftVersion.Release.fromString(it) }
                    .filter { it >= EARLIEST_SUPPORTED_VERSION }
                    .sortedWith { a, b -> a.compareTo(b) }
            }
    }
}