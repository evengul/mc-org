package app.mcorg.infrastructure.repository

import app.mcorg.domain.World
import app.mcorg.domain.Worlds

class WorldsImpl : Worlds, Repository() {
    override fun getWorld(id: Int): World? {
        getConnection().use {
            it.prepareStatement("select * from world where id = ?")
                .apply { setInt(1, id) }
                .executeQuery()
                .apply {
                    if (next()) {
                        val retrievedId = getInt(1)
                        val name = getString(2)
                        return World(retrievedId, name)
                    }
                }
        }
        return null
    }

    override fun deleteWorld(id: Int) {
        getConnection().use {
            it.prepareStatement("delete from world where id = ?")
                .apply { setInt(1, id) }
                .executeUpdate()
        }
    }

    override fun createWorld(name: String): Int {
        getConnection().use {
            val statement = it
                .prepareStatement("insert into world(name) values (?) returning id;")
                .apply { setString(1, name) }

            if (statement.execute()) {
                with(statement.resultSet) {
                    if (next()) {
                        return getInt(1)
                    }
                }
            }
        }

        throw IllegalStateException("Failed to create world")
    }

    override fun changeWorldName(id: Int, name: String) {
        getConnection().use {
            it.prepareStatement("update world set name = ? where id = ?;")
                .apply { setString(1, name); setInt(2, id) }
                .executeUpdate()
        }
    }
}