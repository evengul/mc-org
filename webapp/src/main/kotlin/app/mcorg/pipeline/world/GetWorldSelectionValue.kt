package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import io.ktor.http.Parameters

sealed interface GetWorldSelectionValueFailure : SelectWorldFailure {
    data object NotFound : GetWorldSelectionValueFailure
    data object NotInteger : GetWorldSelectionValueFailure
}

object GetWorldSelectionValue : Step<Parameters, SelectWorldFailure, Int> {
    override fun process(input: Parameters): Result<SelectWorldFailure, Int> {
        return input["worldId"]?.let { str ->
            str.toIntOrNull()?.let { Result.success(it) } ?: Result.failure(GetWorldSelectionValueFailure.NotInteger)
        } ?: Result.failure(
            GetWorldSelectionValueFailure.NotFound
        )
    }
}