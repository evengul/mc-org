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
 * ## Matching is on derived demand (MCO-316)
 *
 * It used to be on declared requirements (`resource_gathering`) — "same granularity as
 * `solved_by_project_id`, and one query instead of one plan derivation per project". That
 * granularity broke down completely for imported schematics, where every declared row is a
 * finished *placed block* and the raw materials exist only in the derived plan. On the YAMS
 * import:
 *
 * | Farm produces  | Declared row | Real plan demand | Edge before |
 * | -------------- | -----------: | ---------------: | ----------- |
 * | `cobblestone`  |            1 |           74,564 | yes — for 1 |
 * | `sand`         |            4 |           52,434 | yes — for 4 |
 * | `gold_nugget`  |            0 |            7,299 | **none**    |
 *
 * So the Cobblestone Generator appeared as supplying one decorative cobblestone while covering
 * the single largest line of gathering work, and the Gold Farm produced no edge at all. The
 * edges that did exist were accidents of decoration.
 *
 * The cost objection still stands, which is why this reads `project_demand` — the plan's own
 * output, materialised when the project's plan was last derived (see `ProjectDemandStore`) —
 * rather than deriving a plan per project here. A project nobody has planned yet contributes
 * no rows and therefore no edges; the roadmap says how many those are rather than quietly
 * drawing a thinner graph.
 */
data class GetFarmSupplyEdgesStep(val worldId: Int) : Step<Unit, AppFailure.DatabaseError, List<ProjectResourceEdge>> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<ProjectResourceEdge>> {
        return DatabaseSteps.query<Unit, List<ProjectResourceEdge>>(
            sql = SafeSQL.select("""
                SELECT
                  d.project_id   AS consumer_id,
                  pc.name        AS consumer_name,
                  pc.state       AS consumer_state,
                  pp.project_id  AS producer_id,
                  prod.name      AS producer_name,
                  d.item_name    AS item_name,
                  d.quantity     AS quantity,
                  prod.state     AS producer_state
                FROM project_demand d
                JOIN projects pc            ON pc.id = d.project_id
                JOIN project_productions pp ON pp.item_id = d.item_id
                JOIN projects prod          ON prod.id = pp.project_id
                WHERE pc.world_id = ?
                  AND prod.world_id = ?
                  AND prod.id <> d.project_id
                  AND prod.state NOT IN (?, ?)
                  -- An explicitly solved requirement already produces an edge via
                  -- GetProjectEdgesStep; deriving a second one would double-count it.
                  AND NOT EXISTS (
                      SELECT 1 FROM resource_gathering rg
                      WHERE rg.project_id = d.project_id
                        AND rg.item_id = d.item_id
                        AND rg.solved_by_project_id IS NOT NULL
                  )
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
                                quantity = resultSet.getLong("quantity"),
                            )
                        )
                    }
                }
            }
        ).process(input)
    }
}
