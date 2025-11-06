package app.mcorg.pipeline.world.commonsteps

import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.world.extractors.toWorld

object GetWorldStep : Step<Int, AppFailure.DatabaseError, World> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, World> {
        return DatabaseSteps.query<Int, World?>(
            sql = getWorldQuery,
            parameterSetter = { statement, worldId -> statement.setInt(1, worldId) },
            resultMapper = { resultSet ->
                if (resultSet.next()) {
                    resultSet.toWorld()
                } else {
                    null
                }
            }
        ).process(input).flatMap {
            if (it == null) {
                Result.failure(AppFailure.DatabaseError.NotFound)
            } else {
                Result.success(it)
            }
        }
    }

    private val getWorldQuery = SafeSQL.select("""
        SELECT 
            world.id, 
            world.name, 
            world.description, 
            world.version, 
            world.created_at, 
            world.updated_at,
            COALESCE(COUNT(projects.id), 0) as total_projects,
            COALESCE(SUM(CASE WHEN projects.stage = 'COMPLETED' THEN 1 ELSE 0 END), 0) as completed_projects
        FROM world 
        LEFT JOIN projects ON world.id = projects.world_id
        WHERE world.id = ?
        group by world.id 
    """.trimIndent())
}