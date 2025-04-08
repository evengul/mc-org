package app.mcorg.pipeline.permission

import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection
import app.mcorg.presentation.handler.GetUsersData

sealed interface GetOtherUsersFailure : GetWorldParticipantsFailure {
    data class Other(val failure: DatabaseFailure) : GetOtherUsersFailure
}

object GetOtherUsersStep : Step<GetUsersData, GetOtherUsersFailure, GetUsersData> {
    override suspend fun process(input: GetUsersData): Result<GetOtherUsersFailure, GetUsersData> {
        return useConnection({ GetOtherUsersFailure.Other(it) }) {
            prepareStatement("select id, username from users where id != ? and id in (select user_id from permission where world_id = ?)")
                .apply {
                    setInt(1, input.currentUserId)
                    setInt(2, input.worldId)
                }
                .executeQuery()
                .let { resultSet ->
                    val users = mutableListOf<User>()
                    while (resultSet.next()) {
                        users.add(
                            User(
                                id = resultSet.getInt("id"),
                                username = resultSet.getString("username"),
                            )
                        )
                    }
                    return@useConnection Result.success(input.copy(users = users))
                }
        }
    }
}