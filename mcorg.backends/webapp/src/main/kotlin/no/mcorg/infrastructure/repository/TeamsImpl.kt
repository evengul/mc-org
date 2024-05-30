package no.mcorg.infrastructure.repository

import no.mcorg.domain.Team
import no.mcorg.domain.Teams

class TeamsImpl : Teams, Repository() {
    override fun getTeam(id: Int): Team? {
        getConnection().use {
            it.prepareStatement("select id,world_id,name from team where id = ?")
            .apply { setInt(1, id) }
            .executeQuery()
            .apply {
                if(next()) {
                    val teamId = getInt(1)
                    val worldId = getInt(2)
                    val name = getString(3)
                    return Team(worldId, teamId, name)
                }
            }
        }
        return null
    }

    override fun deleteTeam(id: Int) {
        getConnection().use {
            it.prepareStatement("delete from team where id = ?")
                .apply { setInt(1, id) }
                .executeUpdate()
        }
    }

    override fun createTeam(worldId: Int, name: String): Int {
        getConnection().use {
            val statement = it
                .prepareStatement("insert into team(world_id,name) values (?,?) returning id")
                .apply { setInt(1, worldId); setString(2, name) }

            if (statement.execute()) {
                with(statement.resultSet) {
                    if (next()) {
                        return getInt(1)
                    }
                }
            }
        }

        throw IllegalStateException("Failed to create team")
    }

    override fun getWorldTeams(worldId: Int): List<Team> {
        getConnection().use {
            it.prepareStatement("select id,name from team where world_id = ?")
            .apply { setInt(1, worldId) }
            .executeQuery()
            .apply {
                val teams = mutableListOf<Team>()

                while (next()) {
                    teams.add(Team(worldId, getInt("id"), getString("name")))
                }

                return teams
            }
        }
    }

    override fun changeTeamName(id: Int, name: String) {
        getConnection().use {
            it.prepareStatement("update team set name = ? where id = ?;")
                .apply { setString(1, name); setInt(2, id); }
                .executeUpdate()
        }
    }
}