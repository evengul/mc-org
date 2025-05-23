package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface UnSelectWorldForAllUsersStepFailure : DeleteWorldFailure {
    data class Other(val failure: DatabaseFailure) : UnSelectWorldForAllUsersStepFailure
}

data class UnSelectWorldForAllUsersStep(val worldId: Int) : Step<Unit, DeleteWorldFailure, Unit> {
    override suspend fun process(input: Unit): Result<DeleteWorldFailure, Unit> {
        return useConnection({ UnSelectWorldForAllUsersStepFailure.Other(it) }) {
            prepareStatement("update users set selected_world = null where selected_world = ?")
                .apply { setInt(1, worldId) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}