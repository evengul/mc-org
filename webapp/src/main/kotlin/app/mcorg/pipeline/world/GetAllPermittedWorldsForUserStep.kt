package app.mcorg.pipeline.world

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.model.permissions.UserPermissions
import app.mcorg.domain.model.worlds.World
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface GetAllPermittedWorldsForUserFailure : GetAllWorldsFailure {
    data object InvalidAuthority : GetAllPermittedWorldsForUserFailure
    data class Other(val failure: DatabaseFailure) : GetAllPermittedWorldsForUserFailure
}

object GetAllPermittedWorldsForUserStep : Step<Int, GetAllPermittedWorldsForUserFailure, UserPermissions> {
    override suspend fun process(input: Int): Result<GetAllPermittedWorldsForUserFailure, UserPermissions> {
        return useConnection({ GetAllPermittedWorldsForUserFailure.Other(it) }) {
            val statement = prepareStatement(
                """
                select world.id, world.name, permission.authority from permission 
                join world on permission.world_id = world.id 
                where permission.user_id = ?;
            """.trimIndent()
            )
            statement.setInt(1, input)
            val resultSet = statement.executeQuery()
            val ownedWorlds = mutableListOf<World>()
            val participantWorlds = mutableListOf<World>()
            while (resultSet.next()) {
                val authority = Authority.fromLevel(resultSet.getInt("authority")) ?: return@useConnection Result.failure(
                    GetAllPermittedWorldsForUserFailure.InvalidAuthority
                )
                val world = World(
                    id = resultSet.getInt("id"),
                    name = resultSet.getString("name"),
                )
                when (authority) {
                    Authority.OWNER -> ownedWorlds.add(world)
                    Authority.ADMIN -> participantWorlds.add(world)
                    Authority.PARTICIPANT -> participantWorlds.add(world)
                }
            }
            return@useConnection Result.success(UserPermissions(input, ownedWorlds, participantWorlds))
        }
    }
}