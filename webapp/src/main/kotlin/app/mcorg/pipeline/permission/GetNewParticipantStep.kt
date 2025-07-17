package app.mcorg.pipeline.permission

import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.GetNewParticipantStepFailure
import app.mcorg.pipeline.useConnection

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