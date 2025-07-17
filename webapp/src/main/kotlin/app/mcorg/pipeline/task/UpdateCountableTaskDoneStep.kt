package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.UpdateCountableTaskDoneFailure
import app.mcorg.pipeline.useConnection

data class UpdateCountableTaskDoneStep(val taskId: Int) : Step<Int, UpdateCountableTaskDoneFailure, Unit> {
    override suspend fun process(input: Int): Result<UpdateCountableTaskDoneFailure, Unit> {
        return useConnection({ UpdateCountableTaskDoneFailure.Other(it) }) {
            prepareStatement("update task set done = least(needed, done + ?) where id = ?")
                .apply { setInt(1, input); setInt(2, taskId) }
                .executeUpdate()
            Result.success()
        }
    }
}