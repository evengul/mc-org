package app.mcorg.pipeline.permission

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.useConnection

data class RemoveUserPermissionsStep(val userId: Int) : Step<Unit, DatabaseFailure, Unit> {
    override suspend fun process(input: Unit): Result<DatabaseFailure, Unit> {
        return useConnection {
            prepareStatement("delete from permission where user_id = ?")
                .apply { setInt(1, userId) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}