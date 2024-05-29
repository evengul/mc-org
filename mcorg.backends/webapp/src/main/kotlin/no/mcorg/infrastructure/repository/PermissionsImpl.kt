package no.mcorg.infrastructure.repository

import no.mcorg.domain.*

class PermissionsImpl(config: AppConfiguration) : Permissions, Repository(config) {
    override fun getPermissions(userId: Int): UserPermissions<Authorized> {
        getConnection()
            .prepareStatement("select permission.world_id, world.name as world_name, " +
                    "permission.team_id, team.world_id as team_world_id, team.name as team_name, " +
                    "permission.pack_id, resource_pack.name as pack_name, " +
                    "authority from permission " +
                    "left join world on permission.world_id = world.id " +
                    "left join team on permission.team_id = team.id " +
                    "left join resource_pack on permission.pack_id = resource_pack.id " +
                    "where user_id = ?")
            .apply { setInt(1, userId) }
            .executeQuery()
            .apply {
                val permissions = mutableMapOf<PermissionLevel, MutableList<Pair<Authorized, Authority>>>()
                permissions[PermissionLevel.TEAM] = mutableListOf()
                permissions[PermissionLevel.WORLD] = mutableListOf()
                permissions[PermissionLevel.PACK] = mutableListOf()

                while (next()) {
                    val worldId = getInt("world_id").takeIf { it > 0 }
                    val teamId = getInt("team_id").takeIf { it > 0 }
                    val projectId = getInt("project_id").takeIf { it > 0 }
                    val packId = getInt("pack_id").takeIf { it > 0 }

                    val authority = getString("authority").toAuthority()

                    if (worldId != null) {
                        val worldName = getString("world_name")
                        permissions[PermissionLevel.WORLD]?.add(Pair(World(worldId, worldName), authority))
                    }

                    if (teamId != null) {
                        val teamWorldId = getInt("team_world_id")
                        val teamName = getString("team_name")
                        permissions[PermissionLevel.TEAM]?.add(Pair(Team(teamWorldId, teamId, teamName), authority))
                    }

                    if (projectId != null) {
                        val projectWorldId = getInt("project_world_id")
                        val projectTeamId = getInt("project_team_id")
                        val projectName = getString("project_name")
                        permissions[PermissionLevel.PROJECT]?.add(Pair(SlimProject(projectWorldId, projectTeamId, projectId, projectName), authority))
                    }

                    if (packId != null) {
                        val packName = getString("pack_name")
                        permissions[PermissionLevel.PACK]?.add(Pair(SlimResourcePack(packId, packName), authority))
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
        getConnection()
            .prepareStatement("""
                select rp.id as resource_pack_id, rp.name as resource_pack_name, rp.server_type, rp.version, p.authority from resource_pack rp
                    left join permission p on rp.id = p.pack_id
                    where p.user_id = ?
            """.trimIndent())
            .apply { setInt(1, userId) }
            .executeQuery()
            .apply {
                val permissions = mutableMapOf<PermissionLevel, MutableList<Pair<ResourcePack, Authority>>>()
                permissions[PermissionLevel.PACK] = mutableListOf()
                while (next()) {
                    val id = getInt("resource_pack_id")
                    val name = getString("resource_pack_name")
                    val serverType = getString("server_type").toServerType()
                    val version = getString("version")
                    val authority = getString("authority").toAuthority()

                    permissions[PermissionLevel.PACK]?.add(Pair(ResourcePack(id, name, version, serverType, mutableListOf()), authority))
                }
                return UserPermissions(userId, permissions)
            }
    }

    override fun hasWorldPermission(userId: Int): Boolean {
        getConnection()
            .prepareStatement("select 1 from permission where user_id = ? and world_id is not null")
            .apply { setInt(1, userId) }
            .executeQuery()
            .apply {
                return next()
            }
    }

    override fun addWorldPermission(userId: Int, worldId: Int, authority: Authority): Int {
        val statement = getConnection()
            .prepareStatement("insert into permission (user_id, authority, world_id, team_id, pack_id) values (?, ?, ?, null, null) returning id")
            .apply { setInt(1, userId); setString(2, authority.name); setInt(3, worldId) }

        if (statement.execute()) {
            with(statement.resultSet) {
                if (next()) {
                    return getInt(1)
                }
            }
        }

        throw IllegalStateException("Failed to add world permission")
    }

    override fun addTeamPermission(userId: Int, teamId: Int, authority: Authority): Int {
        val statement = getConnection()
            .prepareStatement("insert into permission (user_id, authority, world_id, team_id, pack_id) values (?, ?, null, ?, null) returning id")
            .apply { setInt(1, userId); setString(2, authority.name); setInt(3, teamId) }

        if (statement.execute()) {
            with(statement.resultSet) {
                if (next()) {
                    return getInt(1)
                }
            }
        }

        throw IllegalStateException("Failed to add team permission")
    }

    override fun addPackPermission(userId: Int, packId: Int, authority: Authority): Int {
        val statement = getConnection()
            .prepareStatement("insert into permission (user_id, authority, world_id, team_id, pack_id) values (?, ?, null, null, ?) returning id")
            .apply { setInt(1, userId); setString(2, authority.name); setInt(3, packId) }

        if (statement.execute()) {
            with(statement.resultSet) {
                if (next()) {
                    return getInt(1)
                }
            }
        }

        throw IllegalStateException("Failed to add pack permission")
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

    private fun String.toServerType(): ServerType {
        when {
            this == "VANILLA" -> return ServerType.VANILLA
            this == "FABRIC" -> return ServerType.FABRIC
            this == "FORGE" -> return ServerType.FORGE
        }

        throw IllegalArgumentException("Unknown server type: $this")
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