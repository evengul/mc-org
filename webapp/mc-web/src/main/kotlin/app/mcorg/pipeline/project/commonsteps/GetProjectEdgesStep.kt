package app.mcorg.pipeline.project.commonsteps

import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * All producer→consumer edges between projects in a world: resource requirements
 * with a solving project (item-level) plus explicit project_dependencies rows
 * (project-level, no item).
 */
data class GetProjectEdgesStep(val worldId: Int) : Step<Unit, AppFailure.DatabaseError, List<ProjectResourceEdge>> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<ProjectResourceEdge>> {
        return DatabaseSteps.query<Unit, List<ProjectResourceEdge>>(
            sql = SafeSQL.select("""
                SELECT
                  rg.project_id            AS consumer_id,
                  pc.name                  AS consumer_name,
                  pc.state                 AS consumer_state,
                  rg.solved_by_project_id  AS producer_id,
                  pp.name                  AS producer_name,
                  rg.name                  AS item_name,
                  pp.state                 AS producer_state
                FROM resource_gathering rg
                JOIN projects pc ON pc.id = rg.project_id
                JOIN projects pp ON pp.id = rg.solved_by_project_id
                WHERE pc.world_id = ?
                UNION ALL
                SELECT
                  pd.project_id            AS consumer_id,
                  pc.name                  AS consumer_name,
                  pc.state                 AS consumer_state,
                  pd.depends_on_project_id AS producer_id,
                  pp.name                  AS producer_name,
                  NULL                     AS item_name,
                  pp.state                 AS producer_state
                FROM project_dependencies pd
                JOIN projects pc ON pc.id = pd.project_id
                JOIN projects pp ON pp.id = pd.depends_on_project_id
                WHERE pc.world_id = ?
            """.trimIndent()),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
                statement.setInt(2, worldId)
            },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            ProjectResourceEdge(
                                consumerId = resultSet.getInt("consumer_id"),
                                consumerName = resultSet.getString("consumer_name"),
                                consumerState = ProjectState.valueOf(resultSet.getString("consumer_state")),
                                producerId = resultSet.getInt("producer_id"),
                                producerName = resultSet.getString("producer_name"),
                                itemName = resultSet.getString("item_name"),
                                producerState = ProjectState.valueOf(resultSet.getString("producer_state"))
                            )
                        )
                    }
                }
            }
        ).process(input)
    }
}
