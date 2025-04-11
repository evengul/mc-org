package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import io.ktor.http.Parameters

sealed interface GetTaskIdParameterFailure : TaskParamFailure {
    data object TaskIdNotPresent : GetTaskIdParameterFailure
}

object GetTaskIdParameterStep : Step<Parameters, GetTaskIdParameterFailure, Int> {
    override suspend fun process(input: Parameters): Result<GetTaskIdParameterFailure, Int> {
        return when (val taskId = input["taskId"]?.toIntOrNull()) {
            null -> Result.failure(GetTaskIdParameterFailure.TaskIdNotPresent)
            else -> Result.success(taskId)
        }
    }
}