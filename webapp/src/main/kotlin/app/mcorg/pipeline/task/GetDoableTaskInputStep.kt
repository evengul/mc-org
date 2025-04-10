package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import io.ktor.http.Parameters

sealed interface GetDoableTaskInputFailure : CreateDoableTaskFailure {
    data object MissingName : GetDoableTaskInputFailure
}

object GetDoableTaskInputStep : Step<Parameters, GetDoableTaskInputFailure, String> {
    override suspend fun process(input: Parameters): Result<GetDoableTaskInputFailure, String> {
        return when (val name = input["taskName"]) {
            null -> Result.failure(GetDoableTaskInputFailure.MissingName)
            else -> Result.success(name)
        }
    }
}