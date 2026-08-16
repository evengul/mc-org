package app.mcorg.pipeline.resources

import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * The world's farm-scale threshold (MCO-401) — the raw-gather quantity at or above which the
 * plan marks a material as worth a farm.
 *
 * One column rather than [app.mcorg.pipeline.world.commonsteps.GetWorldStep], for the same
 * reason [GetWorldVersionStep] exists: the plan page needs this number and nothing else about
 * the world, and GetWorldStep's aggregate joins every project to count them.
 *
 * A missing world falls back to the default instead of failing. This decorates a plan that has
 * already rendered — losing the marker is a worse outcome than the marker being computed
 * against the default, and the caller has already established the world exists.
 */
object GetFarmScaleThresholdStep : Step<Int, AppFailure, Int> {

    private val query = DatabaseSteps.query<Int, Int?>(
        sql = SafeSQL.select("SELECT farm_scale_threshold FROM world WHERE id = ?"),
        parameterSetter = { ps, worldId -> ps.setInt(1, worldId) },
        resultMapper = { rs -> if (rs.next()) rs.getInt("farm_scale_threshold") else null }
    )

    override suspend fun process(input: Int): Result<AppFailure, Int> = when (val r = query.process(input)) {
        is Result.Success -> Result.success(r.value ?: World.DEFAULT_FARM_SCALE_THRESHOLD)
        is Result.Failure -> r
    }
}
