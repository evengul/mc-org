package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface DeleteTaskStepFailure : DeleteTaskFailure {
    data class Other(val failure: DatabaseFailure) : DeleteTaskStepFailure
}

object DeleteTaskStep : Step<Int, DeleteTaskStepFailure, Unit> {
    override suspend fun process(input: Int): Result<DeleteTaskStepFailure, Unit> {
        return useConnection({ DeleteTaskStepFailure.Other(it) }) {
            prepareStatement("delete from task where id = ?")
                .apply { setInt(1, input) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}