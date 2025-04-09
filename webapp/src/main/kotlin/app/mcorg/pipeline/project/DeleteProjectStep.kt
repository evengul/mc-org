package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

object DeleteProjectStep : Step<Int, DatabaseFailure, Unit> {
    override suspend fun process(input: Int): Result<DatabaseFailure, Unit> {
        return useConnection {
            prepareStatement("delete from project where id = ?")
                .apply { setInt(1, input) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}