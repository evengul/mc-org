package app.mcorg.pipeline.world.commonsteps

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * Stamps when this user last opened a world, driving the "opened Xh ago" hero line and
 * the most-recently-opened-first sort on the Worlds page. Per (user, world), on
 * world_members. Called best-effort on world entry — a failure must not block the page.
 */
data class UpdateWorldLastOpenedStep(val userId: Int) : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Int>(
            SafeSQL.update(
                "UPDATE world_members SET last_opened_at = CURRENT_TIMESTAMP WHERE user_id = ? AND world_id = ?"
            ),
            parameterSetter = { statement, worldId ->
                statement.setInt(1, userId)
                statement.setInt(2, worldId)
            }
        ).process(input)
    }
}
