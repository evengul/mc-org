package app.mcorg.pipeline.permission

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.UpdateWorldPermissionAuditInfoFailure
import app.mcorg.pipeline.useConnection

data class UpdateWorldPermissionAuditInfoStep(val worldId: Int, val currentUsername: String) : Step<Unit, UpdateWorldPermissionAuditInfoFailure, Unit> {
    override suspend fun process(input: Unit): Result<UpdateWorldPermissionAuditInfoFailure, Unit> {
        return useConnection({ UpdateWorldPermissionAuditInfoFailure.Other(it) }) {
            prepareStatement("update world set updated_by = ?, updated_at = now() where id = ?")
                .apply { setString(1, currentUsername); setInt(2, worldId) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}
