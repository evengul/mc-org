package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure

object GetPermittedWorldsStep : Step<Int, DatabaseFailure, List<World>> {
    override suspend fun process(input: Int): Result<DatabaseFailure, List<World>> {
        return DatabaseSteps.query<Int, DatabaseFailure, List<World>>(
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
            errorMapper = { it },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.toWorld())
                    }
                }
            }
        ).process(input)
    }
}
