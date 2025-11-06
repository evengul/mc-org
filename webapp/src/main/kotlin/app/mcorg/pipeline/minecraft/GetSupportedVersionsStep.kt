package app.mcorg.pipeline.minecraft

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import org.slf4j.LoggerFactory

object GetSupportedVersionsStep : Step<Unit, AppFailure.DatabaseError, List<MinecraftVersion.Release>> {
    private val logger = LoggerFactory.getLogger(GetSupportedVersionsStep::class.java)

    suspend fun getSupportedVersions(): List<MinecraftVersion.Release> {
        return runCatching {
            GetSupportedVersionsStep.process(Unit)
                .getOrNull()?.toList() ?: MinecraftVersion.supportedVersions_backup
        }.getOrDefault(MinecraftVersion.supportedVersions_backup)
            .sortedWith { a, b -> b.compareTo(a) }
    }

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<MinecraftVersion.Release>> {
        return DatabaseSteps.query<Unit, List<MinecraftVersion.Release>>(
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
    }
}