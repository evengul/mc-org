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
 * blocks its consumers.
 *
 * ## Except where something already makes the item (MCO-466)
 *
 * That rule read the producer's state alone, which is right until a world has *two* producers
 * of one item. Forever world has a witch farm that has been running for months and a ghast
 * farm still being built, both making gunpowder; judged on its own the ghast farm looked like
 * a prerequisite, so the roadmap said the storage system was blocked for 5 gunpowder while the
 * witch farm supplied that same gunpowder one line above. Worse, MCO-287 had already marked it
 * `SUPPLIED` in the plan — so the roadmap and the project page disagreed about one number.
 *
 * The `superseded` flag is computed here rather than at either call site, for the reason the
 * cycle-order subtraction is: two derivations of "is this a prerequisite" is exactly the bug
 * MCO-461 was filed for, and the roadmap, the Field Log and MCO-299's prerequisite notice all
 * read this one step.
 *
 * It is a flag and not a filter. The relationship is real — the ghast farm genuinely will make
 * gunpowder — and MCO-318 requires both directions to read the same edge set, so dropping the
 * row would make the two columns contradict each other again. It simply stops blocking.
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
 *
 * ## Small edges are still edges (MCO-460)
 *
 * Two farms that each consume a little of the other's output close a loop, and both edges are
 * true: the cobblestone farm really does need 20 gunpowder for TNT, and the witch farm really
 * does need cobblestone. Drawn as equals they make the roadmap contradict itself.
 *
 * The farm-scale threshold is what tells those two claims apart — **20 gunpowder is not a
 * prerequisite; 75,151 cobblestone is** — but it is applied by [RoadmapCycles], *only where a
 * loop actually needs breaking*, and deliberately not here. A Beacon needing 32 iron from the
 * Iron Farm is a perfectly good dependency and belongs on the roadmap; there is nothing
 * contradictory about it, and thresholding every edge to solve a problem that only arises in
 * loops would quietly delete most of the graph.
 *
 * What this query does carry is `roadmap_cycle_order`: a loop no principle could break, that a
 * person then ordered by hand. That is a subtraction from the derived graph, and it lives here
 * so both the roadmap and the project page inherit it from one place — two derivations of "is
 * this a prerequisite" being the bug MCO-461 was filed for.
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
                  prod.state     AS producer_state,
                  -- MCO-466: is something already running making this same item? If so this
                  -- producer is a second source, not a prerequisite. Kept as a flag on the
                  -- edge rather than a filter: the relationship is real and both directions
                  -- must keep reading the same edge set (MCO-318) — it just is not blocking.
                  EXISTS (
                      SELECT 1
                      FROM project_productions op_prod
                      JOIN projects op ON op.id = op_prod.project_id
                      WHERE op_prod.item_id = d.item_id
                        AND op.world_id = pc.world_id
                        AND op.id <> d.project_id
                        AND op.state = ?
                  )              AS superseded
                FROM project_demand d
                JOIN projects pc            ON pc.id = d.project_id
                JOIN project_productions pp ON pp.item_id = d.item_id
                JOIN projects prod          ON prod.id = pp.project_id
                WHERE pc.world_id = ?
                  AND prod.world_id = ?
                  AND prod.id <> d.project_id
                  AND prod.state NOT IN (?, ?)
                  -- A pair a person has already ordered by hand: the edge that would make the
                  -- winner wait is set aside. A subtraction, never an addition (MCO-460).
                  AND NOT EXISTS (
                      SELECT 1 FROM roadmap_cycle_order rco
                      WHERE rco.consumer_project_id = d.project_id
                        AND rco.producer_project_id = pp.project_id
                  )
                  -- An explicitly solved requirement already produces an edge via
                  -- GetProjectEdgesStep; deriving a second one would double-count it.
                  AND NOT EXISTS (
                      SELECT 1 FROM resource_gathering rg
                      WHERE rg.project_id = d.project_id
                        AND rg.item_id = d.item_id
                        AND rg.solved_by_project_id IS NOT NULL
                  )
            """.trimIndent()),
            // Ordinals follow the text of the whole statement, so the superseded EXISTS in the
            // SELECT list takes 1 and everything in the WHERE clause shifts up by one.
            parameterSetter = { statement, _ ->
                statement.setString(1, ProjectState.DONE.name)
                statement.setInt(2, worldId)
                statement.setInt(3, worldId)
                statement.setString(4, ProjectState.CANCELLED.name)
                statement.setString(5, ProjectState.ARCHIVED.name)
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
                                supersededBySupplier = resultSet.getBoolean("superseded"),
                            )
                        )
                    }
                }
            }
        ).process(input)
    }
}
