package app.mcorg.infrastructure.repository

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.api.Permissions
import app.mcorg.domain.model.permissions.UserPermissions
import app.mcorg.domain.model.worlds.World

class PermissionsImpl : Permissions, Repository() {

    override fun getPermissions(userId: Int): UserPermissions {
        getConnection().use { conn ->
            conn.prepareStatement("select permission.world_id, world.name as world_name, " +
                    "authority from permission " +
                    "left join world on permission.world_id = world.id " +
                    "where user_id = ?")
                .apply { setInt(1, userId) }
                .executeQuery()
                .apply {
                    val ownedWorlds = mutableListOf<World>()
                    val participantWorlds = mutableListOf<World>()

                    while (next()) {
                        val worldId = getInt("world_id").takeIf { it > 0 }
                        val worldName = getString("world_name")
                        val authority = getInt("authority").toAuthority()

                        if (worldId != null && worldName != null) {
                            if (authority == Authority.OWNER) ownedWorlds.add(World(worldId, worldName))
                            if (authority === Authority.PARTICIPANT) ownedWorlds.add(World(worldId, worldName))
                        }
                    }

                    return UserPermissions(userId, ownedWorlds, participantWorlds)
                }
        }
    }

    private fun Int.toAuthority(): Authority {
        when {
            this == Authority.OWNER.level -> return Authority.OWNER
            this == Authority.ADMIN.level -> return Authority.ADMIN
            this == Authority.PARTICIPANT.level -> return Authority.PARTICIPANT
        }
        throw IllegalArgumentException("Unknown authority: $this")
    }
}