package app.mcorg.pipeline.minecraft

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import org.slf4j.LoggerFactory

object GetSupportedVersionsStep : Step<Unit, AppFailure.DatabaseError, List<MinecraftVersion.Release>> {
    private val logger = LoggerFactory.getLogger(GetSupportedVersionsStep::class.java)
    private const val CACHE_KEY = "versions"

    @Suppress("UNCHECKED_CAST")
    suspend fun getSupportedVersions(): List<MinecraftVersion.Release> {
        // Check cache first
        val cached = CacheManager.supportedVersions.getIfPresent(CACHE_KEY)
        if (cached != null) {
            return (cached as List<MinecraftVersion.Release>)
                .sortedWith { a, b -> b.compareTo(a) }
        }

        return runCatching {
            GetSupportedVersionsStep.process(Unit)
                .getOrNull()?.toList() ?: MinecraftVersion.supportedVersions_backup
        }.getOrDefault(MinecraftVersion.supportedVersions_backup)
            .sortedWith { a, b -> b.compareTo(a) }
    }

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<MinecraftVersion.Release>> {
        val result = DatabaseSteps.query<Unit, List<MinecraftVersion.Release>>(
            sql = SafeSQL.select("SELECT DISTINCT version FROM minecraft_version"),
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        val versionString = resultSet.getString("version")
                        try {
                            add(MinecraftVersion.Release.fromString(versionString))
                        } catch (e: IllegalArgumentException) {
                            logger.error("Invalid version format in database: $versionString", e)
                        }
                    }
                }
            }
        ).process(Unit)

        // Cache successful results
        if (result is Result.Success) {
            CacheManager.supportedVersions.put(CACHE_KEY, result.value)
        }

        return result
    }
}
