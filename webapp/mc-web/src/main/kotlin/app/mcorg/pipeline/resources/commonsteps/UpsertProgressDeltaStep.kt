package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * Input for incrementing or decrementing progress by a delta, identified by
 * a resource_gathering row id.
 *
 * @param resourceGatheringId identifies the (project_id, item_id) pair and
 *   the required ceiling for clamping.
 * @param delta positive to increment, negative to decrement.
 */
data class UpsertProgressDeltaInput(
    val resourceGatheringId: Int,
    val delta: Int,
)

/**
 * Applies a delta (positive or negative) to the progress entry identified by
 * a resource_gathering row id. The result is clamped to [0, required].
 *
 * If no progress row exists yet the delta is applied from a base of 0.
 * Matches the behaviour of the old
 * `UPDATE resource_gathering SET collected = GREATEST(LEAST(collected + ?, required), 0)`.
 */
object UpsertProgressDeltaStep : Step<UpsertProgressDeltaInput, AppFailure.DatabaseError, Unit> {

    override suspend fun process(input: UpsertProgressDeltaInput): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<UpsertProgressDeltaInput>(
            sql = SafeSQL.with(
                """
                WITH rg AS (
                    SELECT project_id, item_id, required
                    FROM resource_gathering
                    WHERE id = ?
                )
                INSERT INTO resource_gathering_progress (project_id, item_id, collected, updated_at)
                -- First write: base is 0, so clamp(delta) is exactly clamp(0 + delta).
                SELECT rg.project_id,
                       rg.item_id,
                       GREATEST(LEAST(?, rg.required), 0),
                       CURRENT_TIMESTAMP
                FROM rg
                ON CONFLICT (project_id, item_id) DO UPDATE
                    SET collected  = GREATEST(
                            LEAST(resource_gathering_progress.collected + ?, (
                                SELECT required FROM resource_gathering WHERE id = ?
                            )),
                            0
                        ),
                        updated_at = CURRENT_TIMESTAMP
                """.trimIndent()
            ),
            parameterSetter = { stmt, inp ->
                stmt.setInt(1, inp.resourceGatheringId)  // CTE lookup
                stmt.setInt(2, inp.delta)                // INSERT base: clamp(delta, required)
                stmt.setInt(3, inp.delta)                // UPDATE: existing + delta
                stmt.setInt(4, inp.resourceGatheringId)  // UPDATE: required ceiling
            }
        ).process(input).map { }
    }
}
