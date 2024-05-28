package no.mcorg.infrastructure.repository

import no.mcorg.domain.AppConfiguration
import no.mcorg.domain.World
import no.mcorg.domain.Worlds

class WorldsImpl(private val config: AppConfiguration) : Worlds, Repository(config) {
    override fun getWorld(id: Int): World? {
        getConnection()
            .prepareStatement("select * from world where id = ?")
            .apply { setInt(1, id) }
            .executeQuery()
            .apply {
                if (next()) {
                    val retrievedId = getInt(1)
                    val name = getString(2)
                    return World(retrievedId, name)
                }
            }
        return null
    }

    override fun createWorld(name: String): Int {
        val statement = getConnection()
            .prepareStatement("insert into world(name) values (?) returning id;")
            .apply { setString(1, name) }

        if (statement.execute()) {
            with(statement.resultSet) {
                if (next()) {
                    return getInt(1)
                }
            }
        }

        throw IllegalStateException("Failed to create world")
    }

    override fun getUserWorlds(username: String): List<World> {
        getConnection()
            .prepareStatement("select * from world")
            .executeQuery()
            .apply {
                val worldList = mutableListOf<World>()
                while (next()) {
                    val retrievedId = getInt(1)
                    val name = getString(2)
                    worldList.add(World(retrievedId, name))
                }
                return worldList
            }
    }

    override fun changeWorldName(id: Int, name: String) {
        getConnection()
            .prepareStatement("update world set name = ? where id = ?;")
            .apply { setString(1, name); setInt(2, id) }
            .executeUpdate()
    }
}