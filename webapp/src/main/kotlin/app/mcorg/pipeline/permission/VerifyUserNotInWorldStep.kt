package app.mcorg.pipeline.permission

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface VerifyUserNotInWorldStepFailure : AddWorldParticipantFailure {
    data object UserAlreadyExists : VerifyUserNotInWorldStepFailure
    data class Other(val failure: DatabaseFailure) : VerifyUserNotInWorldStepFailure
}

object VerifyUserNotInWorldStep : Step<AddUserInput, VerifyUserNotInWorldStepFailure, AddUserInput> {
    override suspend fun process(input: AddUserInput): Result<VerifyUserNotInWorldStepFailure, AddUserInput> {
        return useConnection({ if (it == DatabaseFailure.NotFound) VerifyUserNotInWorldStepFailure.UserAlreadyExists else VerifyUserNotInWorldStepFailure.Other(it) }) {
            prepareStatement("select 1 from permission where world_id = ? and user_id = ? limit 1")
                .apply {
                    setInt(1, input.worldId)
                    setInt(2, input.userId)
                }
                .executeQuery()
                .apply {
                    if (next()) {
                        return@useConnection Result.failure(VerifyUserNotInWorldStepFailure.UserAlreadyExists)
                    }
                }
            return@useConnection Result.success(input)
        }
    }
}