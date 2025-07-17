package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.DeleteWorldFailure
import app.mcorg.pipeline.failure.RemoveWorldPermissionsForAllUsersStepFailure
import app.mcorg.pipeline.useConnection

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