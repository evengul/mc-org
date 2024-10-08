package app.mcorg.infrastructure.repository

import app.mcorg.domain.GameType
import app.mcorg.domain.World
import app.mcorg.domain.WorldVersion
import app.mcorg.domain.Worlds

class WorldsImpl : Worlds, Repository() {
    override fun getWorld(id: Int): World? {
        getConnection().use {
            it.prepareStatement("select * from world where id = ?")
                .apply { setInt(1, id) }
                .executeQuery()
                .apply {
                    if (next()) {
                        val retrievedId = getInt("id")
                        val name = getString("name")
                        val gameType = GameType.valueOf(getString("game_type"))
                        val version = WorldVersion(
                            getInt("version_main"),
                            getInt("version_secondary"),
                        )
                        val isTechnical = getBoolean("is_technical")
                        return World(retrievedId, name, gameType, version, isTechnical)
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

    override fun createWorld(name: String, gameType: GameType, version: WorldVersion, isTechnical: Boolean): Int {
        getConnection().use {
            val statement = it
                .prepareStatement("insert into world(name, game_type, version_main, version_secondary, is_technical) values (?, ?, ?, ?, ?) returning id;")
                .apply {
                    setString(1, name)
                    setString(2, gameType.name)
                    setInt(3, version.main)
                    setInt(4, version.secondary)
                    setBoolean(5, isTechnical)
                }

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