package app.mcorg.pipeline.task.commonsteps

import app.mcorg.domain.model.task.Task
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.task.extractors.toTask

object GetTaskStep : Step<Int, AppFailure.DatabaseError, Task> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Task> {
        return DatabaseSteps.query<Int, Task?>(
            sql = SafeSQL.select("""
                    SELECT 
                        t.id,
                        t.project_id,
                        t.name,
                        t.requirement_type,
                        t.item_id,
                        t.requirement_item_required_amount,
                        t.requirement_item_collected,
                        t.requirement_action_completed
                    FROM tasks t
                    WHERE t.id = ?
                """.trimIndent()),
            parameterSetter = { statement, taskId ->
                statement.setInt(1, taskId)
            },
            resultMapper = {
                if (it.next()) {
                    it.toTask()
                } else null
            }
        ).process(input).flatMap {
            if (it != null) {
                Result.Companion.success(it)
            } else {
                Result.Companion.failure(AppFailure.DatabaseError.NotFound)
            }
        }
    }
}