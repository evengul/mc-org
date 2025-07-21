package app.mcorg.pipeline.profile

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.useConnection


data class UpdatePlayerAuditingInfoStep(val currentUserName: String) : Step<Int, DatabaseFailure, Unit> {
    override suspend fun process(input: Int): Result<DatabaseFailure, Unit> {
        return useConnection {
            prepareStatement("update users set updated_by = ?, updated_at = now() where id = ?")
                .apply { setString(1, currentUserName); setInt(2, input) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}
