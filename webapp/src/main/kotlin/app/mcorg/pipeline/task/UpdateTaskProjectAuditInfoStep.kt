package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.UpdateProjectAuditInfoFailure
import app.mcorg.pipeline.failure.UpdateTaskProjectAuditInfoFailure
import app.mcorg.pipeline.project.UpdateProjectAuditInfoStep

data class UpdateTaskProjectAuditInfoStep(val currentUsername: String, val projectId: Int) :
    Step<Unit, UpdateTaskProjectAuditInfoFailure, Unit> {
    override suspend fun process(input: Unit): Result<UpdateTaskProjectAuditInfoFailure, Unit> {
        return when(val result = UpdateProjectAuditInfoStep(currentUsername, projectId).process(Unit)) {
            is Result.Failure -> Result.failure(
                when(result.error) {
                    is UpdateProjectAuditInfoFailure.Other -> UpdateTaskProjectAuditInfoFailure.Other(result.error.failure)
                }
            )
            is Result.Success -> Result.success(Unit)
        }
    }
}
