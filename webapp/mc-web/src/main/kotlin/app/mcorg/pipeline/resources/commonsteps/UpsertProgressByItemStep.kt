package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * Input for applying a delta to the progress entry identified by (projectId, itemId).
 *
 * The [required] ceiling is passed in from the plan — derived activities have no
 * resource_gathering row, so we cannot look it up from there.
 *
 * @param projectId the project that owns this progress entry.
 * @param itemId the Minecraft item id (e.g. "minecraft:iron_ingot").
 * @param delta positive to increment, negative to decrement.
 * @param required upper clamp for the collected value.
 */
data class UpsertProgressByItemInput(
    val projectId: Int,
    val itemId: String,
    val delta: Int,
    val required: Long,
)

/**
 * Applies a delta (positive or negative) to the progress entry identified by
 * (projectId, itemId). The result is clamped to [0, required].
 *
 * If no progress row exists yet the delta is applied from a base of 0.
 * Does NOT require a resource_gathering row — derived plan activities can use this.
 */
object UpsertProgressByItemStep : Step<UpsertProgressByItemInput, AppFailure.DatabaseError, Unit> {

    override suspend fun process(input: UpsertProgressByItemInput): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<UpsertProgressByItemInput>(
            sql = SafeSQL.insert(
                """
                INSERT INTO resource_gathering_progress (project_id, item_id, collected, updated_at)
                VALUES (?, ?, GREATEST(LEAST(?, ?), 0), CURRENT_TIMESTAMP)
                ON CONFLICT (project_id, item_id) DO UPDATE
                    SET collected  = GREATEST(
                            LEAST(resource_gathering_progress.collected + ?, ?),
                            0
                        ),
                        updated_at = CURRENT_TIMESTAMP
                """.trimIndent()
            ),
            parameterSetter = { stmt, inp ->
                stmt.setInt(1, inp.projectId)         // project_id
                stmt.setString(2, inp.itemId)          // item_id
                stmt.setInt(3, inp.delta)              // INSERT base: clamp(delta, required)
                stmt.setLong(4, inp.required)          // INSERT: required ceiling
                stmt.setInt(5, inp.delta)              // UPDATE: existing + delta
                stmt.setLong(6, inp.required)          // UPDATE: required ceiling
            }
        ).process(input).map { }
    }
}
