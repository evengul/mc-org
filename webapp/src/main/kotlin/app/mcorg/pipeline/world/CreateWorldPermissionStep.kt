package app.mcorg.pipeline.world

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.pipeline.ContextAwareStep
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface CreateWorldPermissionFailure : CreateWorldFailure {
    data class Other(val failure: DatabaseFailure) : CreateWorldPermissionFailure
}

data class CreateWorldPermissionStepInput(
    val worldId: Int,
    val authority: Authority,
)

object CreateWorldPermissionStep : ContextAwareStep<CreateWorldPermissionStepInput, Int, CreateWorldFailure, Int> ({ input, context ->
    useConnection({ CreateWorldPermissionFailure.Other(it) }) {
        prepareStatement("insert into permission (world_id, authority, user_id) values (?, ?, ?)")
            .apply {
                setInt(1, input.worldId)
                setInt(2, input.authority.level)
                setInt(3, context)
            }
            .executeUpdate()
        return@useConnection Result.success(input.worldId)
    }
})