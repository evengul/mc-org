package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.TaskStage
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface UpdateTaskStageStepFailure : UpdateTaskStageFailure {
    data class Other(val failure: DatabaseFailure) : UpdateTaskStageStepFailure
}

data class UpdateTaskStageStep(val taskId: Int) : Step<TaskStage, UpdateTaskStageStepFailure, Unit> {
    override suspend fun process(input: TaskStage): Result<UpdateTaskStageStepFailure, Unit> {
        return useConnection({ UpdateTaskStageStepFailure.Other(it) }) {
            prepareStatement("update task set stage = ? where id = ?")
                .apply {
                    setString(1, input.id)
                    setInt(2, taskId)
                }
                .executeUpdate()

            Result.success(Unit)
        }
    }
}