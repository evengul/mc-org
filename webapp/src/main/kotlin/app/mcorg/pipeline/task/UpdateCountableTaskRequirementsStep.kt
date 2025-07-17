package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.UpdateCountableTaskRequirementsStepFailure
import app.mcorg.pipeline.useConnection

object UpdateCountableTaskRequirementsStep : Step<GetCountableTasksEditValues, UpdateCountableTaskRequirementsStepFailure, Unit> {
    override suspend fun process(input: GetCountableTasksEditValues): Result<UpdateCountableTaskRequirementsStepFailure, Unit> {
        return useConnection({ UpdateCountableTaskRequirementsStepFailure.Other(it) }) {
            prepareStatement("update task set needed = ?, done = ? where id = ?")
                .apply {
                    setInt(1, input.needed)
                    setInt(2, input.done)
                    setInt(3, input.taskId)
                }
                .executeUpdate()
            return@useConnection Result.success(Unit)
        }
    }
}