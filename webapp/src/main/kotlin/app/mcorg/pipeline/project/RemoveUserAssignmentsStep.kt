package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.useConnection

data class RemoveUserAssignmentsStep(val userId: Int) : Step<Unit, DatabaseFailure, Unit> {
    override suspend fun process(input: Unit): Result<DatabaseFailure, Unit> {
        return useConnection {
            prepareStatement("update task set assignee = null where assignee = ?")
                .apply { setInt(1, userId) }
                .executeUpdate()
            prepareStatement("update project set assignee = null where assignee = ?")
                .apply { setInt(1, userId); }
                .executeUpdate()
            return@useConnection Result.Companion.success(Unit)
        }
    }
}