package app.mcorg.pipeline.permission

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface VerifyUserExistsStepFailure : AddWorldParticipantFailure {
    data object UserDoesNotExist : VerifyUserExistsStepFailure
    data class Other(val failure: DatabaseFailure) : VerifyUserExistsStepFailure
}

object VerifyUsernameExistsStep : Step<String, VerifyUserExistsStepFailure, Int> {
    override suspend fun process(input: String): Result<VerifyUserExistsStepFailure, Int> {
        return useConnection({ if (it == DatabaseFailure.NotFound) VerifyUserExistsStepFailure.UserDoesNotExist else VerifyUserExistsStepFailure.Other(it) }) {
            prepareStatement("select id from users where username = ? limit 1")
                .apply { setString(1, input) }
                .executeQuery()
                .apply {
                    if (next()) {
                        return@useConnection Result.success(getInt(1))
                    }
                }
            return@useConnection Result.failure(VerifyUserExistsStepFailure.UserDoesNotExist)
        }
    }
}