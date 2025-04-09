package app.mcorg.pipeline.user

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

data class DeleteUserStep(val userId: Int) : Step<Unit, DatabaseFailure, Unit> {
    override suspend fun process(input: Unit): Result<DatabaseFailure, Unit> {
        return useConnection {
            prepareStatement("delete from users where id = ?")
                .apply { setInt(1, userId) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}