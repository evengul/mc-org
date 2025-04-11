package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface AssignUserToProjectStepFailure : AssignProjectFailure {
    data class Other(val failure: DatabaseFailure) : AssignUserToProjectStepFailure
}

data class AssignUserToProjectStep(val projectId: Int) : Step<Int, AssignUserToProjectStepFailure, Unit> {
    override suspend fun process(input: Int): Result<AssignUserToProjectStepFailure, Unit> {
        return useConnection({ AssignUserToProjectStepFailure.Other(it) }) {
            prepareStatement("update project set assignee = ? where id = ?")
                .apply {
                    setInt(1, input)
                    setInt(2, projectId)
                }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}
