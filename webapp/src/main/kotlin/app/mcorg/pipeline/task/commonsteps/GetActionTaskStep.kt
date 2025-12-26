package app.mcorg.pipeline.task.commonsteps

import app.mcorg.domain.model.task.ActionTask
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.task.extractors.toActionTask

object GetActionTaskStep : Step<Int, AppFailure.DatabaseError, ActionTask> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, ActionTask> {
        return DatabaseSteps.query<Int, ActionTask?>(
            sql = SafeSQL.select("""
                    SELECT 
                        t.id,
                        t.project_id,
                        t.name,
                        t.completed
                    FROM action_task t
                    WHERE t.id = ?
                """.trimIndent()),
            parameterSetter = { statement, taskId ->
                statement.setInt(1, taskId)
            },
            resultMapper = {
                if (it.next()) {
                    it.toActionTask()
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