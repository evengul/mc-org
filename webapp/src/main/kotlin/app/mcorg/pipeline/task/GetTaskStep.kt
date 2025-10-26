package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.Task
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.failure.DatabaseFailure

object GetTaskStep : Step<Int, DatabaseFailure, Task> {
    override suspend fun process(input: Int): Result<DatabaseFailure, Task> {
        return DatabaseSteps.query<Int, DatabaseFailure, Task?>(
            sql = getTaskQuery,
            parameterSetter = { statement, taskId ->
                statement.setInt(1, taskId)
            },
            errorMapper = { it },
            resultMapper = {
                if (it.next()) {
                    it.toTask()
                } else null
            }
        ).process(input).flatMap {
            if (it != null) {
                Result.success(it)
            } else {
                Result.failure(DatabaseFailure.NotFound)
            }
        }
    }
}