package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.GetCountableTaskInputStepFailure
import io.ktor.http.Parameters

object GetCountableTaskInputStep : Step<Parameters, GetCountableTaskInputStepFailure, Pair<String, Int>> {
    override suspend fun process(input: Parameters): Result<GetCountableTaskInputStepFailure, Pair<String, Int>> {
        val name = input["taskName"]
        val amount = input["amount"]?.toIntOrNull()

        if (name == null && amount == null) {
            return Result.failure(GetCountableTaskInputStepFailure.MultipleMissing(listOf(GetCountableTaskInputStepFailure.NameNotPresent, GetCountableTaskInputStepFailure.AmountNotPresent)))
        }

        if (name == null) {
            return Result.failure(GetCountableTaskInputStepFailure.NameNotPresent)
        }
        if (amount == null) {
            return Result.failure(GetCountableTaskInputStepFailure.AmountNotPresent)
        }

        return Result.success(name to amount)
    }
}