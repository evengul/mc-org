package app.mcorg.pipeline.minecraft

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import org.slf4j.LoggerFactory

object GetSupportedVersionsStep : Step<Unit, DatabaseFailure, List<MinecraftVersion.Release>> {
    private val logger = LoggerFactory.getLogger(GetSupportedVersionsStep::class.java)

    suspend fun getSupportedVersions(): List<MinecraftVersion.Release> {
        return runCatching {
            GetSupportedVersionsStep.process(Unit)
                .getOrNull()?.toList() ?: MinecraftVersion.supportedVersions_backup
        }.getOrDefault(MinecraftVersion.supportedVersions_backup)
            .sortedWith { a, b -> b.compareTo(a) }
    }

    override suspend fun process(input: Unit): Result<DatabaseFailure, List<MinecraftVersion.Release>> {
        return DatabaseSteps.query<Unit, DatabaseFailure, List<MinecraftVersion.Release>>(
            sql = SafeSQL.select("SELECT DISTINCT version FROM minecraft_items"),
            errorMapper = { it },
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