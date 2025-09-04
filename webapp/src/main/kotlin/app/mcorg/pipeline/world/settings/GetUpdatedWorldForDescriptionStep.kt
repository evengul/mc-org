package app.mcorg.pipeline.world.settings

import app.mcorg.domain.model.world.World
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.toWorld

object GetUpdatedWorldForDescriptionStep : Step<Int, UpdateWorldDescriptionFailures, World> {
    override suspend fun process(input: Int): Result<UpdateWorldDescriptionFailures, World> {
        val getWorldQuery = SafeSQL.select("""
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
            GROUP BY world.id 
        """.trimIndent())

        return DatabaseSteps.query<Int, UpdateWorldDescriptionFailures, World?>(
            sql = getWorldQuery,
            parameterSetter = { statement, _ ->
                statement.setInt(1, input)
            },
            errorMapper = { UpdateWorldDescriptionFailures.DatabaseError(it) },
            resultMapper = { if (it.next()) it.toWorld() else null }
        ).process(input).flatMap { world ->
            if (world != null) {
                Result.success(world)
            } else {
                Result.failure(UpdateWorldDescriptionFailures.DatabaseError(
                    app.mcorg.pipeline.failure.DatabaseFailure.NotFound
                ))
            }
        }
    }
}
