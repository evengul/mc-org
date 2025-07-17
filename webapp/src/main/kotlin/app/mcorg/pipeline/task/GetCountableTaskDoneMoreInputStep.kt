package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.GetCountableTaskDoneMoreInputFailure
import io.ktor.http.Parameters

object GetCountableTaskDoneMoreInputStep : Step<Parameters, GetCountableTaskDoneMoreInputFailure, Int> {
    override suspend fun process(input: Parameters): Result<GetCountableTaskDoneMoreInputFailure, Int> {
        return when (val done = input["done"]?.toIntOrNull()) {
            null -> Result.failure(GetCountableTaskDoneMoreInputFailure.MissingDone)
            else -> Result.success(done)
        }
    }
}