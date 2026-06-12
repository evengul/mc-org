package app.mcorg.pipeline.project.commonsteps

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * The world's resume project: the most recently touched ACTIVE project,
 * or null when the world has no active projects.
 */
data class GetResumeProjectIdStep(val worldId: Int) : Step<Unit, AppFailure.DatabaseError, Int?> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Int?> {
        return DatabaseSteps.query<Unit, Int?>(
            sql = SafeSQL.select("""
                SELECT id
                FROM projects
                WHERE world_id = ? AND state = 'ACTIVE'
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
            """.trimIndent()),
            parameterSetter = { statement, _ -> statement.setInt(1, worldId) },
            resultMapper = { resultSet ->
                if (resultSet.next()) resultSet.getInt("id") else null
            }
        ).process(input)
    }
}
