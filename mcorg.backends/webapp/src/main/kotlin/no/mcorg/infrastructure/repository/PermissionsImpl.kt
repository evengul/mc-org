package no.mcorg.infrastructure.repository

import no.mcorg.domain.*

class PermissionsImpl(private val config: AppConfiguration) : Permissions, Repository(config) {
    override fun getPermission(userId: Int): UserPermissions<SimplyAuthorized> {
        getConnection()
            .prepareStatement("select * from permission where user_id = ?")
            .apply { setInt(1, userId) }
            .executeQuery()
            .apply {
                val permissions = mutableMapOf<PermissionLevel, MutableList<Pair<SimplyAuthorized, Authority>>>()
                permissions[PermissionLevel.TEAM] = mutableListOf()
                permissions[PermissionLevel.WORLD] = mutableListOf()
                permissions[PermissionLevel.PACK] = mutableListOf()

                while (next()) {
                    val authority = getString(2).toAuthority()

                    val worldId = getInt(3).takeIf { it > 0 }
                    val teamId = getInt(4).takeIf { it > 0 }
                    val packId = getInt(5).takeIf { it > 0 }

                    if (worldId != null) {
                        permissions[PermissionLevel.WORLD]!!.add(Pair(SimplyAuthorized(worldId), authority))
                    }

                    if (teamId != null) {
                        permissions[PermissionLevel.TEAM]!!.add(Pair(SimplyAuthorized(teamId), authority))
                    }

                    if (packId != null) {
                        permissions[PermissionLevel.PACK]!!.add(Pair(SimplyAuthorized(packId), authority))
                    }
                }

                return UserPermissions(userId, permissions)
            }
    }

    override fun getWorldPermissions(userId: Int): UserPermissions<World> {
        getConnection()
            .prepareStatement("select authority, world_id, w.name from permission p inner join world w on p.world_id = w.id where user_id = ?")
            .apply { setInt(1, userId) }
            .executeQuery()
            .apply {
                val permissions = mutableMapOf<PermissionLevel, MutableList<Pair<World, Authority>>>()
                permissions[PermissionLevel.WORLD] = mutableListOf()

                while (next()) {
                    val authority = getString(1).toAuthority()

                    val worldId = getInt(2)
                    val worldName = getString(3)

                    permissions[PermissionLevel.WORLD]!!.add(Pair(World(worldId, worldName), authority))
                }

                return UserPermissions(userId, permissions)
            }
    }

    override fun getTeamPermissions(userId: Int): UserPermissions<Team> {
        getConnection()
            .prepareStatement("select authority, t.world_id, team_id, t.name from permission p inner join team t on p.team_id = t.id where user_id = ?")
            .apply { setInt(1, userId) }
            .executeQuery()
            .apply {
                val permissions = mutableMapOf<PermissionLevel, MutableList<Pair<Team, Authority>>>()
                permissions[PermissionLevel.TEAM] = mutableListOf()

                while (next()) {
                    val authority = getString(1).toAuthority()

                    val worldId = getInt(2)
                    val teamId = getInt(3)
                    val teamName = getString(4)

                    permissions[PermissionLevel.TEAM]!!.add(Pair(Team(worldId, teamId, teamName), authority))
                }

                return UserPermissions(userId, permissions)
            }
    }

    override fun getPackPermissions(userId: Int): UserPermissions<ResourcePack> {
        TODO()
    }

    override fun addWorldPermission(userId: Int, worldId: Int, authority: Authority) {
        getConnection()
            .prepareStatement("insert into permission (user_id, authority, world_id, team_id, pack_id) values (?, ?, ?, null, null)")
            .apply { setInt(1, userId); setString(2, authority.name); setInt(3, worldId) }
            .executeUpdate()
    }

    override fun addTeamPermission(userId: Int, teamId: Int, authority: Authority) {
        getConnection()
            .prepareStatement("insert into permission (user_id, authority, world_id, team_id, pack_id) values (?, ?, null, ?, null)")
            .apply { setInt(1, userId); setString(2, authority.name); setInt(3, teamId) }
            .executeUpdate()
    }

    override fun addPackPermission(userId: Int, packId: Int, authority: Authority) {
        getConnection()
            .prepareStatement("insert into permission (user_id, authority, world_id, team_id, pack_id) values (?, ?, null, null, ?)")
            .apply { setInt(1, userId); setString(2, authority.name); setInt(3, packId) }
            .executeUpdate()
    }

    override fun changeWorldPermission(userId: Int, worldId: Int, authority: Authority) {
        getConnection()
            .prepareStatement("update permission set authority = ? where user_id = ? and world_id = ?")
            .apply { setString(1, authority.name); setInt(2, userId); setInt(3, worldId) }
            .executeUpdate()
    }

    override fun changeTeamPermission(userId: Int, teamId: Int, authority: Authority) {
        getConnection()
            .prepareStatement("update permission set authority = ? where user_id = ? and team_id = ?")
            .apply { setString(1, authority.name); setInt(2, userId); setInt(3, teamId) }
            .executeUpdate()
    }

    override fun changePackPermission(userId: Int, packId: Int, authority: Authority) {
        getConnection()
            .prepareStatement("update permission set authority = ? where user_id = ? and pack_id = ?")
            .apply { setString(1, authority.name); setInt(2, userId); setInt(3, packId) }
            .executeUpdate()
    }

    override fun removeWorldPermission(userId: Int, worldId: Int) {
        getConnection()
            .prepareStatement("delete from permission where user_id = ? and world_id = ?")
            .apply { setInt(1, userId); setInt(2, worldId) }
            .executeUpdate()
    }

    override fun removeTeamPermission(userId: Int, teamId: Int) {
        getConnection()
            .prepareStatement("delete from permission where user_id = ? and team_id = ?")
            .apply { setInt(1, userId); setInt(2, teamId) }
            .executeUpdate()
    }

    override fun removePackPermission(userId: Int, packId: Int) {
        getConnection()
            .prepareStatement("delete from permission where user_id = ? and pack_id = ?")
            .apply { setInt(1, userId); setInt(2, packId) }
            .executeUpdate()
    }

    private fun String.toAuthority(): Authority {
        when {
            this == "OWNER" -> return Authority.OWNER
            this == "ADMIN" -> return Authority.ADMIN
            this == "PARTICIPANT" -> return Authority.PARTICIPANT
        }
        throw IllegalArgumentException("Unknown authority: $this")
    }
}