package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.GetCountableTasksEditInputFailure
import io.ktor.http.Parameters

data class GetCountableTasksEditValues(
    val taskId: Int,
    val needed: Int,
    val done: Int
)

object GetCountableTasksEditInputStep : Step<Parameters, GetCountableTasksEditInputFailure, GetCountableTasksEditValues> {
    override suspend fun process(input: Parameters): Result<GetCountableTasksEditInputFailure, GetCountableTasksEditValues> {
        val errors = mutableSetOf<GetCountableTasksEditInputFailure>()

        val taskId = input["id"]?.toIntOrNull()
        val needed = input["needed"]?.toIntOrNull()
        val done = input["done"]?.toIntOrNull()

        if (taskId != null && done != null && needed != null) {
            return Result.success(GetCountableTasksEditValues(
                taskId = taskId,
                needed = needed,
                done = done
            ))
        }

        if (taskId == null) {
            errors.add(GetCountableTasksEditInputFailure.TaskIdNotPresent)
        }
        if (needed == null) {
            errors.add(GetCountableTasksEditInputFailure.RequirementsNotPresent)
        }
        if (done == null) {
            errors.add(GetCountableTasksEditInputFailure.DoneNotPresent)
        }

        if (errors.isNotEmpty()) {
            return if (errors.size == 1) {
                Result.failure(errors.first())
            } else {
                Result.failure(GetCountableTasksEditInputFailure.MultipleMissing(errors))
            }
        }

        throw IllegalStateException("This should never happen, as we already checked for null values")
    }
}