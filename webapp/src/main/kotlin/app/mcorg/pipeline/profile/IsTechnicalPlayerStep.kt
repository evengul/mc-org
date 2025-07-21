package app.mcorg.pipeline.profile

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.useConnection

data class IsTechnicalPlayerStep(val userId: Int) : Step<Boolean, DatabaseFailure, Unit> {
    override suspend fun process(input: Boolean): Result<DatabaseFailure, Unit> {
        return useConnection {
            prepareStatement("update users set technical_player = ? where id = ?")
                .apply {
                    setBoolean(1, input)
                    setInt(2, userId)
                }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}