package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.AssignTaskStepFailure
import app.mcorg.pipeline.useConnection

data class AssignTaskStep(val taskId: Int) : Step<Int, AssignTaskStepFailure, Unit> {
    override suspend fun process(input: Int): Result<AssignTaskStepFailure, Unit> {
        return useConnection({ AssignTaskStepFailure.Other(it) }) {
            prepareStatement("update task set assignee = ? where id = ?")
                .apply {
                    setInt(1, input)
                    setInt(2, taskId)
                }
                .executeUpdate()
            Result.success(Unit)
        }
    }
}