package app.mcorg.pipeline.world.commonsteps

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.domain.pipeline.Step

/** A single project in the Worlds-page hero "active projects" peek. */
data class WorldProjectPeek(
    val name: String,
    val state: ProjectState
)

/**
 * Top non-terminal projects for a world's hero peek: active work first, then paused,
 * then pending, most-recently-updated within each. Terminal projects (done/cancelled/
 * archived) are excluded. Limited to a handful — the hero only shows a glance.
 */
data class GetWorldProjectPeekStep(val limit: Int = 3) : Step<Int, AppFailure.DatabaseError, List<WorldProjectPeek>> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, List<WorldProjectPeek>> {
        return DatabaseSteps.query<Int, List<WorldProjectPeek>>(
            sql = SafeSQL.select(
                """
                SELECT name, state
                FROM projects
                WHERE world_id = ? AND state IN ('ACTIVE', 'PAUSED', 'PENDING')
                ORDER BY
                    CASE state WHEN 'ACTIVE' THEN 0 WHEN 'PAUSED' THEN 1 ELSE 2 END,
                    updated_at DESC
                LIMIT ?
                """.trimIndent()
            ),
            parameterSetter = { statement, worldId ->
                statement.setInt(1, worldId)
                statement.setInt(2, limit)
            },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            WorldProjectPeek(
                                name = resultSet.getString("name"),
                                state = ProjectState.valueOf(resultSet.getString("state"))
                            )
                        )
                    }
                }
            }
        ).process(input)
    }
}
