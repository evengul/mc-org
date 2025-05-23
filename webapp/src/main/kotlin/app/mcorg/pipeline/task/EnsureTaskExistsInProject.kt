package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface EnsureTaskExistsInProjectFailure : TaskParamFailure {
    data object TaskNotFound : EnsureTaskExistsInProjectFailure
    data class Other(val failure: DatabaseFailure) : EnsureTaskExistsInProjectFailure
}

data class EnsureTaskExistsInProject(val projectId: Int) : Step<Int, EnsureTaskExistsInProjectFailure, Int> {
    override suspend fun process(taskId: Int): Result<EnsureTaskExistsInProjectFailure, Int> {
        return useConnection({ EnsureTaskExistsInProjectFailure.Other(it) }) {
            prepareStatement("select id from task where id = ? and project_id = ?")
                .apply { setInt(1, taskId); setInt(2, projectId) }
                .executeQuery()
                .takeIf { it.next() }
                ?.let { Result.success(taskId) }
                ?: Result.failure(EnsureTaskExistsInProjectFailure.TaskNotFound)
        }
    }
}
