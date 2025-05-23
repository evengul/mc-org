package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.TaskStage
import app.mcorg.domain.model.task.TaskStages
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import io.ktor.http.Parameters

sealed interface GetTaskStageInputFailure : UpdateTaskStageFailure {
    data object MissingStage : GetTaskStageInputFailure
    data object InvalidStage : GetTaskStageInputFailure
}

object GetTaskStageInputStep : Step<Parameters, GetTaskStageInputFailure, TaskStage> {
    override suspend fun process(input: Parameters): Result<GetTaskStageInputFailure, TaskStage> {
        return when (input["stage"]) {
            null -> Result.failure(GetTaskStageInputFailure.MissingStage)
            "TODO" -> Result.success(TaskStages.TODO)
            "IN_PROGRESS" -> Result.success(TaskStages.IN_PROGRESS)
            "DONE" -> Result.success(TaskStages.DONE)
            else -> Result.failure(GetTaskStageInputFailure.InvalidStage)
        }
    }
}