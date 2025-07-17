package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.RemoveProjectAssignmentStepFailure
import app.mcorg.pipeline.useConnection

data class RemoveProjectAssignmentStep(val projectId: Int) : Step<Unit, RemoveProjectAssignmentStepFailure, Unit> {
    override suspend fun process(input: Unit): Result<RemoveProjectAssignmentStepFailure, Unit> {
        return useConnection({ RemoveProjectAssignmentStepFailure.Other(it) }) {
            prepareStatement("update project set assignee = null where id = ?")
                .apply { setInt(1, projectId) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}