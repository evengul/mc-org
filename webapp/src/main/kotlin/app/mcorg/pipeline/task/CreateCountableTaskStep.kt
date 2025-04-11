package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.getReturnedId
import app.mcorg.pipeline.useConnection

sealed interface CreateCountableStepFailure : CreateCountableTaskFailure {
    data class Other(val failure: DatabaseFailure) : CreateCountableStepFailure
}

data class CreateCountableTaskStep(val projectId: Int) : Step<Pair<String, Int>, CreateCountableStepFailure, Int> {
    override suspend fun process(input: Pair<String, Int>): Result<CreateCountableStepFailure, Int> {
        return useConnection({ CreateCountableStepFailure.Other(it) }) {
            prepareStatement("insert into task (project_id, name, needed, done, type) values (?, ?, ?, 0, 'COUNTABLE') returning id")
                .apply { setInt(1, projectId); setString(2, input.first); setInt(3, input.second) }
                .getReturnedId(CreateCountableStepFailure.Other(DatabaseFailure.NoIdReturned))
        }
    }
}