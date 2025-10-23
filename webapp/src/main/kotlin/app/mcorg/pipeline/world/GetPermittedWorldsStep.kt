package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure

data class GetPermittedWorldsInput(
    val userId: Int,
    val query: String = ""
)

object GetPermittedWorldsStep : Step<GetPermittedWorldsInput, DatabaseFailure, List<World>> {
    override suspend fun process(input: GetPermittedWorldsInput): Result<DatabaseFailure, List<World>> {
        return DatabaseSteps.query<GetPermittedWorldsInput, DatabaseFailure, List<World>>(
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
                WHERE wm.user_id = ? AND (? = '' OR LOWER(w.name) ILIKE '%' || ? || '%' OR LOWER(w.description) ILIKE '%' || ? || '%')
                GROUP BY w.id, w.name, w.description, w.version, w.created_at, w.updated_at
                ORDER BY w.name
            """.trimIndent()),
            parameterSetter = { statement, inputData ->
                statement.setInt(1, inputData.userId)

                val normalizedSearch = input.query.trim().lowercase()
                statement.setString(2, normalizedSearch)
                statement.setString(3, normalizedSearch)
                statement.setString(4, normalizedSearch)
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
