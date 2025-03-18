package app.mcorg.infrastructure.repository

import app.mcorg.domain.model.worlds.World
import app.mcorg.domain.api.Worlds

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

    override fun worldExists(id: Int): Boolean {
        getConnection().use {
            it.prepareStatement("select 1 from world where id = ? limit 1")
                .apply { setInt(1, id) }
                .executeQuery()
                .apply {
                    return next()
                }
        }
    }

    override fun worldExistsByName(name: String): Boolean {
        getConnection().use {
            it.prepareStatement("select 1 from world where name = ? limit 1")
                .apply { setString(1, name) }
                .executeQuery()
                .apply {
                    return next()
                }
        }
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