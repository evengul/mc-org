package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.UpdateTaskAuditInfoFailure
import app.mcorg.pipeline.useConnection

data class UpdateTaskAuditInfoStep(val currentUsername: String, val taskId: Int) : Step<Unit, UpdateTaskAuditInfoFailure, Unit> {
    override suspend fun process(input: Unit): Result<UpdateTaskAuditInfoFailure, Unit> {
        return useConnection({ UpdateTaskAuditInfoFailure.Other(it) }) {
            prepareStatement("update task set updated_by = ?, updated_at = now() where id = ?")
                .apply { setString(1, currentUsername); setInt(2, taskId) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}
