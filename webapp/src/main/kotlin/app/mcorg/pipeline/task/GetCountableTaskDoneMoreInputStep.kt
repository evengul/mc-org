package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import io.ktor.http.Parameters

sealed interface GetCountableTaskDoneMoreInputFailure : EditDoneMoreTaskFailure {
    data object MissingDone : GetCountableTaskDoneMoreInputFailure
}

object GetCountableTaskDoneMoreInputStep : Step<Parameters, GetCountableTaskDoneMoreInputFailure, Int> {
    override suspend fun process(input: Parameters): Result<GetCountableTaskDoneMoreInputFailure, Int> {
        return when (val done = input["done"]?.toIntOrNull()) {
            null -> Result.failure(GetCountableTaskDoneMoreInputFailure.MissingDone)
            else -> Result.success(done)
        }
    }
}