package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * Input for upserting a progress entry via a resource_gathering row id.
 *
 * @param resourceGatheringId the resource_gathering.id that identifies both
 *   the (project_id, item_id) pair and the required ceiling for clamping.
 * @param value the raw collected value requested by the user (will be clamped
 *   to [0, required] by the SQL expression, matching the old behaviour).
 */
data class UpsertProgressByRgIdInput(
    val resourceGatheringId: Int,
    val value: Int,
)

/**
 * Upserts the collected progress for a (project_id, item_id) pair identified
 * by a resource_gathering row id.
 *
 * The collected value is clamped to [0, required] so callers do not need to
 * validate the range. Matches the behaviour of the old
 * `UPDATE resource_gathering SET collected = LEAST(GREATEST(?,0),required)`.
 */
object UpsertProgressStep : Step<UpsertProgressByRgIdInput, AppFailure.DatabaseError, Unit> {

    override suspend fun process(input: UpsertProgressByRgIdInput): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<UpsertProgressByRgIdInput>(
            sql = SafeSQL.insert(
                """
                INSERT INTO resource_gathering_progress (project_id, item_id, collected, updated_at)
                SELECT rg.project_id,
                       rg.item_id,
                       LEAST(GREATEST(?, 0), rg.required),
                       CURRENT_TIMESTAMP
                FROM resource_gathering rg
                WHERE rg.id = ?
                ON CONFLICT (project_id, item_id) DO UPDATE
                    SET collected  = EXCLUDED.collected,
                        updated_at = EXCLUDED.updated_at
                """.trimIndent()
            ),
            parameterSetter = { stmt, inp ->
                stmt.setInt(1, inp.value)
                stmt.setInt(2, inp.resourceGatheringId)
            }
        ).process(input).map { }
    }
}
