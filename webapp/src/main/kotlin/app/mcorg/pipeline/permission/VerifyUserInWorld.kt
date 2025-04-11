package app.mcorg.pipeline.permission

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface VerifyUserInWorldFailure : RemoveWorldParticipantFailure {
    data object NotPresent : VerifyUserInWorldFailure
    data class Other(val failure: DatabaseFailure) : VerifyUserInWorldFailure
}

data class VerifyUserInWorldInput(
    val userId: Int,
    val worldId: Int,
)

object VerifyUserInWorld : Step<VerifyUserInWorldInput, VerifyUserInWorldFailure, VerifyUserInWorldInput> {
    override suspend fun process(input: VerifyUserInWorldInput): Result<VerifyUserInWorldFailure, VerifyUserInWorldInput> {
        return useConnection({ VerifyUserInWorldFailure.Other(it) }) {
            prepareStatement("select 1 from permission where user_id = ? and world_id = ? limit 1")
                .apply { setInt(1, input.userId); setInt(2, input.worldId) }
                .executeQuery()
                .apply {
                    if (next()) {
                        return@useConnection Result.success(input)
                    }
                }
            return@useConnection Result.failure(VerifyUserInWorldFailure.NotPresent)
        }
    }
}