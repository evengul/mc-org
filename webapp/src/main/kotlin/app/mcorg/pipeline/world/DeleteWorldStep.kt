package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.ContextAwareStep
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface DeleteWorldStepFailure : DeleteWorldFailure {
    data class Other(val failure: DatabaseFailure) : DeleteWorldStepFailure
}

object DeleteWorldStep : ContextAwareStep<Unit, Int, DeleteWorldFailure, Unit>({ _, worldId ->
    useConnection({ DeleteWorldStepFailure.Other(it) }) {
        prepareStatement("delete from world where id = ?")
            .apply { setInt(1, worldId) }
            .executeUpdate()
        return@useConnection Result.success()
    }
})