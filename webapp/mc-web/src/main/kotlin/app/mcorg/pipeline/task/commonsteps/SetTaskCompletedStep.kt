package app.mcorg.pipeline.task.commonsteps

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class SetTaskCompletedInput(val taskId: Int, val completed: Boolean)

/**
 * Sets an action task's completion to an absolute value. Unlike the toggle used by the HTML
 * handler, the mod API is idempotent (`PUT { completed }`), so it sets the value directly.
 * Returns the affected-row count.
 */
object SetTaskCompletedStep : Step<SetTaskCompletedInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: SetTaskCompletedInput): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<SetTaskCompletedInput>(
            sql = SafeSQL.update(
                """
                UPDATE action_task
                SET completed = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """.trimIndent()
            ),
            parameterSetter = { stmt, inp ->
                stmt.setBoolean(1, inp.completed)
                stmt.setInt(2, inp.taskId)
            }
        ).process(input)
    }
}
