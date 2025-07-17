package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.GetWorldSelectionValueFailure
import app.mcorg.pipeline.failure.SelectWorldFailure
import io.ktor.http.Parameters

object GetWorldSelectionValue : Step<Parameters, SelectWorldFailure, Int> {
    override suspend fun process(input: Parameters): Result<SelectWorldFailure, Int> {
        return input["worldId"]?.let { str ->
            str.toIntOrNull()?.let { Result.success(it) } ?: Result.failure(GetWorldSelectionValueFailure.NotInteger)
        } ?: Result.failure(
            GetWorldSelectionValueFailure.NotFound
        )
    }
}