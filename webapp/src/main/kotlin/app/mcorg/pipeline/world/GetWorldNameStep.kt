package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import io.ktor.http.Parameters

sealed interface GetWorldNameFailure : CreateWorldFailure {
    data object NotPresent : GetWorldNameFailure
}

object GetWorldNameStep : Step<Parameters, GetWorldNameFailure, String> {
    override suspend fun process(input: Parameters): Result<GetWorldNameFailure, String> {
        return when (val worldName = input["worldName"]) {
            null -> Result.failure(GetWorldNameFailure.NotPresent)
            else -> Result.success(worldName)
        }
    }
}