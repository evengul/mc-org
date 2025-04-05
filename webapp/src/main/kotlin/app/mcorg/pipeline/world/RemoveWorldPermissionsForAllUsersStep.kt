package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.ContextAwareStep
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.useConnection

sealed interface RemoveWorldPermissionsForAllUsersStepFailure : DeleteWorldFailure {
    data class Other(val failure: DatabaseFailure) : RemoveWorldPermissionsForAllUsersStepFailure
}

object RemoveWorldPermissionsForAllUsersStep : ContextAwareStep<Unit, Int, DeleteWorldFailure, Unit>({ _, worldId ->
    useConnection({ RemoveWorldPermissionsForAllUsersStepFailure.Other(it) }) {
        prepareStatement("delete from permission where world_id = ?")
            .apply { setInt(1, worldId) }
            .executeUpdate()
        return@useConnection Result.success()
    }
})