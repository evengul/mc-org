package app.mcorg.infrastructure.repository

import app.mcorg.domain.model.permissions.Authority
import app.mcorg.domain.api.Permissions
import app.mcorg.domain.model.permissions.UserPermissions
import app.mcorg.domain.model.users.User
import app.mcorg.domain.model.worlds.World

class PermissionsImpl : Permissions, Repository() {
    override fun hasWorldPermission(userId: Int, authority: Authority, worldId: Int): Boolean {
        return getConnection()
            .use {
                it.prepareStatement("select 1 from permission where user_id = ? and authority <= ? and world_id = ?")
                    .apply { setInt(1, userId); setInt(2, authority.level); setInt(3, worldId) }
                    .executeQuery()
                    .next()
            }
    }

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

    override fun hasAnyWorldPermission(userId: Int): Boolean {
        getConnection().use {
            it.prepareStatement("select 1 from permission where user_id = ? and world_id is not null")
                .apply { setInt(1, userId) }
                .executeQuery()
                .apply {
                    return next()
                }
        }
    }

    override fun addWorldPermission(userId: Int, worldId: Int, authority: Authority): Int {
        getConnection().use {
            val statement = it
                .prepareStatement("insert into permission (user_id, authority, world_id) values (?, ?, ?) returning id")
                .apply { setInt(1, userId); setInt(2, authority.level); setInt(3, worldId) }

            if (statement.execute()) {
                with(statement.resultSet) {
                    if (next()) {
                        return getInt(1)
                    }
                }
            }
        }

        throw IllegalStateException("Failed to add world permission")
    }

    override fun changeWorldPermission(userId: Int, worldId: Int, authority: Authority) {
        getConnection().use {
            it.prepareStatement("update permission set authority = ? where user_id = ? and world_id = ?")
                .apply { setInt(1, authority.level); setInt(2, userId); setInt(3, worldId) }
                .executeUpdate()
        }
    }

    override fun removeWorldPermission(userId: Int, worldId: Int) {
        getConnection().use {
            it.prepareStatement("delete from permission where user_id = ? and world_id = ?")
                .apply { setInt(1, userId); setInt(2, worldId) }
                .executeUpdate()
        }
    }

    override fun removeWorldPermissionForAll(worldId: Int) {
        getConnection().use {
            it.prepareStatement("delete from permission where world_id = ?")
                .apply { setInt(1, worldId) }
                .executeUpdate()
        }
    }

    override fun getUsersInWorld(worldId: Int): List<User> {
        getConnection().use {
            it.prepareStatement("select u.id as user_id, u.username as username from permission p join users u on p.user_id = u.id where world_id = ?")
                .apply { setInt(1, worldId) }
                .executeQuery()
                .apply {
                    val list = mutableListOf<User>()
                    while (next()) {
                        list.add(User(getInt("user_id"), getString("username")))
                    }
                    return list
                }
        }
    }

    override fun removeUserPermissions(userId: Int) {
        getConnection().use {
            it.prepareStatement("delete from permission where user_id = ?")
                .apply { setInt(1, userId) }
                .executeUpdate()
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