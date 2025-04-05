package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.ContextAwareStep
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.WithContext
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface SelectWorldStepFailure : SelectWorldFailure {
    data class Other(val failure: DatabaseFailure) : SelectWorldFailure
}

object SelectWorldStep : ContextAwareStep<Int, Int, SelectWorldFailure, Int>({ worldId, userId ->
    useConnection({ SelectWorldStepFailure.Other(it) }) {
        prepareStatement("update users set selected_world = ? where id = ?")
            .apply { setInt(1, worldId); setInt(2, userId) }
            .executeUpdate()
        return@useConnection Result.success(worldId)
    }
})

object SelectWorldAfterCreation : ContextAwareStep<Int, Int, CreateWorldFailure, Int>({ worldId, userId ->
    SelectWorldStep.process(WithContext(worldId, userId))
})