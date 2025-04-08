package app.mcorg.pipeline.permission

import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface GetNewParticipantStepFailure : AddWorldParticipantFailure {
    data object NotFound : GetNewParticipantStepFailure
    data class Other(val failure: DatabaseFailure) : GetNewParticipantStepFailure
}

object GetNewParticipantStep : Step<Int, GetNewParticipantStepFailure, User> {
    override suspend fun process(input: Int): Result<GetNewParticipantStepFailure, User> {
        return useConnection({ GetNewParticipantStepFailure.Other(it) }) {
            prepareStatement("select id, username from users where id = ?")
                .apply {
                    setInt(1, input)
                }
                .executeQuery()
                .apply {
                    if (next()) {
                        return@useConnection Result.success(User(getInt("id"), getString("username")))
                    }
                }
            return@useConnection Result.failure(GetNewParticipantStepFailure.NotFound)
        }
    }
}