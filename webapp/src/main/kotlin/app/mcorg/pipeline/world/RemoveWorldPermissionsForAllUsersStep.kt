package app.mcorg.pipeline.world

import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.useConnection

sealed interface RemoveWorldPermissionsForAllUsersStepFailure : DeleteWorldFailure {
    data class Other(val failure: DatabaseFailure) : RemoveWorldPermissionsForAllUsersStepFailure
}

data class RemoveWorldPermissionsForAllUsersStep(val worldId: Int) : Step<Unit, DeleteWorldFailure, Unit> {
    override suspend fun process(input: Unit): Result<DeleteWorldFailure, Unit> {
        return useConnection({ RemoveWorldPermissionsForAllUsersStepFailure.Other(it) }) {
            prepareStatement("delete from permission where world_id = ?")
                .apply { setInt(1, worldId) }
                .executeUpdate()
            return@useConnection Result.success()
        }
    }
}