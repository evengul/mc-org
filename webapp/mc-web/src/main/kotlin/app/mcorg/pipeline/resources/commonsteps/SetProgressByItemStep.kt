package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * Input for setting an absolute collected value for the progress entry identified by
 * (projectId, itemId). The required ceiling is looked up from the matching resource_gathering row.
 *
 * @param projectId the project that owns this progress entry.
 * @param itemId the Minecraft item id (e.g. "minecraft:iron_ingot").
 * @param collected the absolute value to set (clamped to [0, required]).
 */
data class SetProgressByItemInput(
    val projectId: Int,
    val itemId: String,
    val collected: Int,
)

/**
 * Sets an absolute collected value for a (project_id, item_id) pair, clamped to [0, required].
 *
 * Unlike [UpsertProgressByItemStep] (which applies a delta and takes the ceiling as a parameter),
 * this reads the required ceiling from the item's resource_gathering row — so a caller that only
 * knows the item id (e.g. the mod sync endpoint) does not need to pass it. If no resource_gathering
 * row exists for the (project, item) pair the statement affects zero rows (unknown items are
 * silently ignored). Mirrors the SafeSQL ON CONFLICT pattern of [UpsertProgressStep].
 *
 * Returns the affected-row count (1 when the item is a tracked resource, 0 otherwise).
 */
object SetProgressByItemStep : Step<SetProgressByItemInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: SetProgressByItemInput): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<SetProgressByItemInput>(
            sql = SafeSQL.insert(
                """
                INSERT INTO resource_gathering_progress (project_id, item_id, collected, updated_at)
                SELECT rg.project_id,
                       rg.item_id,
                       LEAST(GREATEST(?, 0), rg.required),
                       CURRENT_TIMESTAMP
                FROM resource_gathering rg
                WHERE rg.project_id = ? AND rg.item_id = ?
                ON CONFLICT (project_id, item_id) DO UPDATE
                    SET collected  = EXCLUDED.collected,
                        updated_at = EXCLUDED.updated_at
                """.trimIndent()
            ),
            parameterSetter = { stmt, inp ->
                stmt.setInt(1, inp.collected)
                stmt.setInt(2, inp.projectId)
                stmt.setString(3, inp.itemId)
            }
        ).process(input)
    }
}
