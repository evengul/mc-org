package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.model.world.World
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import java.sql.ResultSet

sealed interface GetPermittedWorldsError {
    data object DatabaseError : GetPermittedWorldsError
}

object GetPermittedWorldsStep {
    operator fun invoke(): Step<Int, GetPermittedWorldsError, List<World>> {
        return DatabaseSteps.query(
            sql = SafeSQL.select("""
                SELECT 
                    w.id, 
                    w.name, 
                    w.description, 
                    w.version, 
                    w.created_at, 
                    w.updated_at,
                    COALESCE(COUNT(DISTINCT p.id), 0) as total_projects,
                    COALESCE(COUNT(DISTINCT CASE WHEN p.stage = 'COMPLETED' THEN p.id END), 0) as completed_projects
                FROM world w
                INNER JOIN world_members wm ON w.id = wm.world_id
                LEFT JOIN projects p ON w.id = p.world_id
                WHERE wm.user_id = ?
                GROUP BY w.id, w.name, w.description, w.version, w.created_at, w.updated_at
                ORDER BY w.name
            """.trimIndent()),
            parameterSetter = { statement, userId ->
                statement.setInt(1, userId)
            },
            errorMapper = { _ -> GetPermittedWorldsError.DatabaseError },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.toWorld())
                    }
                }
            }
        )
    }

    private fun ResultSet.toWorld(): World {
        return World(
            id = getInt("id"),
            name = getString("name"),
            description = getString("description") ?: "",
            version = MinecraftVersion.fromString(getString("version")),
            completedProjects = getInt("completed_projects"),
            totalProjects = getInt("total_projects"),
            createdAt = getTimestamp("created_at").toInstant().atZone(java.time.ZoneOffset.UTC),
            updatedAt = getTimestamp("updated_at").toInstant().atZone(java.time.ZoneOffset.UTC)
        )
    }
}
