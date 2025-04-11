package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface DeleteWorldStepFailure : DeleteWorldFailure {
    data class Other(val failure: DatabaseFailure) : DeleteWorldStepFailure
}

data class DeleteWorldStep(val worldId: Int) : Step<Unit, DeleteWorldFailure, Unit> {
    override suspend fun process(input: Unit): Result<DeleteWorldFailure, Unit> {
        return useConnection({ DeleteWorldStepFailure.Other(it) }) {
            prepareStatement("delete from world where id = ?")
                .apply { setInt(1, worldId) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}