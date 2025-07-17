package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.UpdateProjectAuditInfoFailure
import app.mcorg.pipeline.useConnection

data class UpdateProjectAuditInfoStep(val currentUsername: String, val worldId: Int) : Step<Unit, UpdateProjectAuditInfoFailure, Unit> {
    override suspend fun process(input: Unit): Result<UpdateProjectAuditInfoFailure, Unit> {
        return useConnection({ UpdateProjectAuditInfoFailure.Other(it) }) {
            prepareStatement("update project set updated_by = ?, updated_at = now() where id = ?")
                .apply { setString(1, currentUsername); setInt(2, worldId) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}
