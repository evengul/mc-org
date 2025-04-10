package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.getReturnedId
import app.mcorg.pipeline.useConnection

sealed interface CreateDoableTaskStepFailure : CreateDoableTaskFailure {
    data class Other(val failure: DatabaseFailure) : CreateDoableTaskStepFailure
}

data class CreateDoableTaskStep(val projectId: Int) : Step<String, CreateDoableTaskStepFailure, Int> {
    override suspend fun process(input: String): Result<CreateDoableTaskStepFailure, Int> {
        return useConnection({ CreateDoableTaskStepFailure.Other(it) }) {
            prepareStatement("insert into task (project_id, name, needed, done, type) values (?, ?, 1, 0, 'DOABLE') returning id")
                .apply {
                    setInt(1, projectId)
                    setString(2, input)
                }
                .getReturnedId(CreateDoableTaskStepFailure.Other(DatabaseFailure.NoIdReturned))
        }
    }
}