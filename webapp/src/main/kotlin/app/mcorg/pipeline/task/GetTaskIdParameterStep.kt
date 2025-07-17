package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.GetTaskIdParameterFailure
import io.ktor.http.Parameters

object GetTaskIdParameterStep : Step<Parameters, GetTaskIdParameterFailure, Int> {
    override suspend fun process(input: Parameters): Result<GetTaskIdParameterFailure, Int> {
        return when (val taskId = input["taskId"]?.toIntOrNull()) {
            null -> Result.failure(GetTaskIdParameterFailure.TaskIdNotPresent)
            else -> Result.success(taskId)
        }
    }
}