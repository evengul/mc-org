package app.mcorg.pipeline.project

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.DeleteProjectStepFailure
import app.mcorg.pipeline.useConnection

object DeleteProjectStep : Step<Int, DeleteProjectStepFailure, Unit> {
    override suspend fun process(input: Int): Result<DeleteProjectStepFailure, Unit> {
        return useConnection({ DeleteProjectStepFailure.Other(it) }) {
            prepareStatement("delete from project where id = ?")
                .apply { setInt(1, input) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}