package app.mcorg.pipeline.world.commonsteps

import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.world.extractors.toWorld

data class GetPermittedWorldsInput(
    val userId: Int,
)

object GetPermittedWorldsStep : Step<GetPermittedWorldsInput, AppFailure.DatabaseError, List<World>> {
    override suspend fun process(input: GetPermittedWorldsInput): Result<AppFailure.DatabaseError, List<World>> {
        return DatabaseSteps.query<GetPermittedWorldsInput, List<World>>(
            sql = SafeSQL.select("""
                SELECT
                    w.id,
                    w.name,
                    w.description,
                    w.version,
                    w.created_at,
                    w.updated_at,
                    wm.pinned,
                    wm.last_opened_at,
                    w.farm_scale_threshold,
                    w.preferred_wood_species,
                    COALESCE(COUNT(DISTINCT p.id), 0) as total_projects,
                    COALESCE(COUNT(DISTINCT CASE WHEN p.stage = 'COMPLETED' THEN p.id END), 0) as completed_projects,
                    ${projectTallyColumns("p")}
                FROM world w
                INNER JOIN world_members wm ON w.id = wm.world_id
                LEFT JOIN projects p ON w.id = p.world_id
                WHERE wm.user_id = ?
                GROUP BY w.id, w.name, w.description, w.version, w.created_at, w.updated_at, wm.pinned, wm.last_opened_at, w.farm_scale_threshold, w.preferred_wood_species
                -- Worlds page ordering: a user's pinned worlds first, then the most-recently-opened.
                ORDER BY wm.pinned DESC, wm.last_opened_at DESC NULLS LAST, w.updated_at DESC, w.name ASC
            """.trimIndent()),
            parameterSetter = { statement, inputData ->
                statement.setInt(1, inputData.userId)
            },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            resultSet.toWorld().copy(
                                pinned = resultSet.getBoolean("pinned"),
                                lastOpenedAt = resultSet.getTimestamp("last_opened_at")
                                    ?.toInstant()?.atZone(java.time.ZoneOffset.UTC)
                            )
                        )
                    }
                }
            }
        ).process(input)
    }
}
