package app.mcorg.infrastructure.repository

import app.mcorg.domain.*
import java.sql.ResultSet
import java.sql.Types

private const val AUTHOR_SEPARATOR = ";"

class ContraptionsImpl : Contraptions, Repository() {
    override suspend fun getContraption(id: Int): Contraption? {
        getConnection().use { conn ->
            conn.prepareStatement("SELECT * FROM contraption WHERE id = ?")
                .apply { setInt(1, id) }
                .executeQuery()
                .apply {
                    if (next()) {
                        return getContraption()
                    }
                }
        }
        return null
    }

    override suspend fun getContraptions(): List<Contraption> {
        getConnection()
            .use { conn ->
                conn.prepareStatement("SELECT * FROM contraption where archived = false")
                    .executeQuery()
                    .apply {
                        val list = mutableListOf<Contraption>()
                        while (next()) {
                            list.add(getContraption())
                        }
                        return list
                    }
            }
    }

    private fun ResultSet.getContraption(): Contraption {
        return Contraption(
            id = getInt("id"),
            name = getString("name"),
            description = getString("description"),
            archived = getBoolean("archived"),
            authors = getString("authors").split(AUTHOR_SEPARATOR).toList(),
            worksInGame = GameType.valueOf(getString("game_type")),
            worksInVersion = ContraptionVersion(
                lowerBound = WorldVersion(
                    getInt("version_lower_major"),
                    getInt("version_lower_minor")
                ),
                upperBound = getInt("version_upper_major").takeIf { it > 0 }
                    ?.let { WorldVersion(it, getInt("version_upper_minor")) }
            ),
            schematicUrl = getString("schematic_url"),
            worldDownloadUrl = getString("world_download_url")
        )
    }

    override suspend fun createContraption(
        name: String,
        description: String?,
        archived: Boolean,
        authors: List<String>,
        gameType: GameType,
        version: ContraptionVersion,
        schematicUrl: String?,
        worldDownloadUrl: String?
    ): Int {
        getConnection().use { conn ->
            val statement = conn.prepareStatement("INSERT INTO contraption (name, description, archived, authors, game_type, version_lower_major, version_lower_minor, version_upper_major, version_upper_minor, schematic_url, world_download_url) VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id")
                .apply {
                    setString(1, name)
                    setString(2, description)
                    setBoolean(3, archived)
                    setString(4, authors.joinToString(AUTHOR_SEPARATOR))
                    setString(5, gameType.name)
                    setInt(6, version.lowerBound.major)
                    setInt(7, version.lowerBound.minor)
                    if (version.upperBound == null) {
                        setNull(8, Types.INTEGER)
                        setNull(9, Types.INTEGER)
                    } else {
                        setInt(8, version.upperBound.major)
                        setInt(9, version.upperBound.minor)
                    }
                    setString(10, schematicUrl)
                    setString(11, worldDownloadUrl)
                }
            if (statement.execute()) {
                with(statement.resultSet) {
                    if (next()) {
                        return getInt(1)
                    }
                }
            }
        }

        throw IllegalStateException("Could not create contraption")
    }

    override suspend fun deleteContraption(id: Int) {
        getConnection().use { conn -> conn
            .prepareStatement("DELETE FROM contraption WHERE id = ?")
            .apply { setInt(1, id) }
            .executeUpdate()
        }
    }
}