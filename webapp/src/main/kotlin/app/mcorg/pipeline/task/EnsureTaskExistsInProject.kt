package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.EnsureTaskExistsInProjectFailure
import app.mcorg.pipeline.useConnection

data class EnsureTaskExistsInProject(val projectId: Int) : Step<Int, EnsureTaskExistsInProjectFailure, Int> {
    override suspend fun process(input: Int): Result<EnsureTaskExistsInProjectFailure, Int> {
        return useConnection({ EnsureTaskExistsInProjectFailure.Other(it) }) {
            prepareStatement("select id from task where id = ? and project_id = ?")
                .apply { setInt(1, input); setInt(2, projectId) }
                .executeQuery()
                .takeIf { it.next() }
                ?.let { Result.success(input) }
                ?: Result.failure(EnsureTaskExistsInProjectFailure.TaskNotFound)
        }
    }
}
