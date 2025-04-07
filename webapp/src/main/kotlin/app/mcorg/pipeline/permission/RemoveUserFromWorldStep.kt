package app.mcorg.pipeline.permission

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface RemoveUserFromWorldFailure : RemoveWorldParticipantFailure {
    data class Other(val failure: DatabaseFailure) : RemoveUserFromWorldFailure
}

data class RemoveUserFromWorldInput(
    val userId: Int,
    val worldId: Int,
)

object RemoveUserFromWorldStep : Step<RemoveUserFromWorldInput, RemoveUserFromWorldFailure, Unit> {
    override fun process(input: RemoveUserFromWorldInput): Result<RemoveUserFromWorldFailure, Unit> {
        return useConnection({ RemoveUserFromWorldFailure.Other(it) }) {
            prepareStatement("delete from permission where user_id = ? and world_id = ?")
                .apply {
                    setInt(1, input.userId)
                    setInt(2, input.worldId)
                }
                .executeUpdate()
            return@useConnection Result.success(Unit)
        }
    }
}