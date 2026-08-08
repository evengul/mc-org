package app.mcorg.pipeline.project.commonsteps

import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * Producer→consumer edges implied by farm supply (MCO-288, on top of MCO-287).
 *
 * A project that needs an item another project *produces* depends on that project, even
 * though nobody declared the link: `solved_by_project_id` was never set and there is no
 * `project_dependencies` row. The plan page already says so — MCO-299's notice promises
 * "32 Iron Ingot will come from Iron Farm once it is running" — so a roadmap that omitted
 * these would contradict the page the user just came from.
 *
 * Blocking follows the same rule as every other edge ([ProjectResourceEdge.isBlocking]):
 * an operational (DONE) farm supplies now and blocks nothing; a farm still being built
 * blocks its consumers. That falls straight out of the producer's state — no special case.
 *
 * Matching is on declared requirements (`resource_gathering`), not on the derived plan:
 * same granularity as `solved_by_project_id`, and one query instead of one plan derivation
 * per project. Ignored requirements (MCO-247) are not demand and are excluded.
 */
data class GetFarmSupplyEdgesStep(val worldId: Int) : Step<Unit, AppFailure.DatabaseError, List<ProjectResourceEdge>> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<ProjectResourceEdge>> {
        return DatabaseSteps.query<Unit, List<ProjectResourceEdge>>(
            sql = SafeSQL.select("""
                SELECT DISTINCT
                  rg.project_id  AS consumer_id,
                  pc.name        AS consumer_name,
                  pc.state       AS consumer_state,
                  pp.project_id  AS producer_id,
                  prod.name      AS producer_name,
                  rg.name        AS item_name,
                  prod.state     AS producer_state
                FROM resource_gathering rg
                JOIN projects pc         ON pc.id = rg.project_id
                JOIN project_productions pp ON pp.item_id = rg.item_id
                JOIN projects prod       ON prod.id = pp.project_id
                WHERE pc.world_id = ?
                  AND prod.world_id = ?
                  AND prod.id <> rg.project_id
                  AND rg.ignored = FALSE
                  AND prod.state NOT IN (?, ?)
                  -- An explicitly solved requirement already produces an edge via
                  -- GetProjectEdgesStep; deriving a second one would double-count it.
                  AND rg.solved_by_project_id IS NULL
            """.trimIndent()),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
                statement.setInt(2, worldId)
                statement.setString(3, ProjectState.CANCELLED.name)
                statement.setString(4, ProjectState.ARCHIVED.name)
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
                                producerState = ProjectState.valueOf(resultSet.getString("producer_state")),
                            )
                        )
                    }
                }
            }
        ).process(input)
    }
}
