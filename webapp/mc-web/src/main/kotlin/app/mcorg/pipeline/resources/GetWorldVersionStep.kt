package app.mcorg.pipeline.resources

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * Looks up the Minecraft version string for a world by its id.
 * Returns [AppFailure.DatabaseError.NotFound] when the world does not exist or has no version set.
 *
 * Extracted from [GenerateGatheringPlanStep] so handlers that need the graph independently
 * (e.g. the drill-chain view) can obtain the version without re-running the full plan.
 */
object GetWorldVersionStep : Step<Int, AppFailure, String> {

    private val query = DatabaseSteps.query<Int, String?>(
        sql = SafeSQL.select("SELECT version FROM world WHERE id = ?"),
        parameterSetter = { ps, worldId -> ps.setInt(1, worldId) },
        resultMapper = { rs -> if (rs.next()) rs.getString("version") else null }
    )

    override suspend fun process(input: Int): Result<AppFailure, String> {
        return when (val r = query.process(input)) {
            is Result.Success -> r.value
                ?.let { Result.success(it) }
                ?: Result.failure(AppFailure.DatabaseError.NotFound)
            is Result.Failure -> r
        }
    }
}
