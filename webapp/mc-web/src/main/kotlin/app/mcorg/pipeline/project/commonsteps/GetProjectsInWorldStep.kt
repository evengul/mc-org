package app.mcorg.pipeline.project.commonsteps

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * Returns (id, name) pairs of projects in the given world, excluding the current project.
 * Used by the resource detail panel to populate the "Use existing project" source option.
 */
data class GetProjectsInWorldStep(val excludeProjectId: Int) : Step<Int, AppFailure.DatabaseError, List<Pair<Int, String>>> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, List<Pair<Int, String>>> {
        return DatabaseSteps.query<Int, List<Pair<Int, String>>>(
            sql = SafeSQL.select(
                """
                SELECT id, name
                FROM projects
                WHERE world_id = ? AND id != ?
                ORDER BY name
                """.trimIndent()
            ),
            parameterSetter = { statement, worldId ->
                statement.setInt(1, worldId)
                statement.setInt(2, excludeProjectId)
            },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.getInt("id") to resultSet.getString("name"))
                    }
                }
            }
        ).process(input)
    }
}
