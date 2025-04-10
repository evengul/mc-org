package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface RemoveTaskAssignmentStepFailure : AssignTaskFailure {
    data class Other(val failure: DatabaseFailure) : RemoveTaskAssignmentStepFailure
}

object RemoveTaskAssignmentStep : Step<Int, RemoveTaskAssignmentStepFailure, Unit> {
    override suspend fun process(input: Int): Result<RemoveTaskAssignmentStepFailure, Unit> {
        return useConnection({ RemoveTaskAssignmentStepFailure.Other(it) }) {
            prepareStatement("update task set assignee = null where id = ?")
                .apply { setInt(1, input) }
                .executeUpdate()
            Result.success()
        }
    }
}