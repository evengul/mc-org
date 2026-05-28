package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class ClearResourceSourceStep(val resourceGatheringId: Int) : Step<Unit, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<Unit>(
            sql = SafeSQL.update(
                """
                UPDATE resource_gathering
                SET source_type = NULL, solved_by_project_id = NULL, updated_at = NOW()
                WHERE id = ?
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, resourceGatheringId)
            }
        ).process(input).map { }
    }
}
