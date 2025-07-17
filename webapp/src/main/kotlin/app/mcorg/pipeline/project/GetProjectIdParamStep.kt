package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.GetProjectIdParamFailure
import io.ktor.http.Parameters

data object GetProjectIdParamStep : Step<Parameters, GetProjectIdParamFailure, Int> {
    override suspend fun process(input: Parameters): Result<GetProjectIdParamFailure, Int> {
        return when(val projectId = input["projectId"]?.toIntOrNull()) {
            null -> Result.failure(GetProjectIdParamFailure.ProjectIdNotPresent)
            else -> Result.success(projectId)
        }
    }
}