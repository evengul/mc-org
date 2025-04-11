package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import io.ktor.http.Parameters

sealed interface GetWorldIdParameterFailure : WorldParamFailure {
    data object WorldIdNotPresent : GetWorldIdParameterFailure
}

object GetWorldIdParameterStep : Step<Parameters, GetWorldIdParameterFailure, Int> {
    override suspend fun process(input: Parameters): Result<GetWorldIdParameterFailure, Int> {
        return when(val worldId = input["worldId"]?.toIntOrNull()) {
            null -> Result.failure(GetWorldIdParameterFailure.WorldIdNotPresent)
            else -> Result.success(worldId)
        }
    }
}