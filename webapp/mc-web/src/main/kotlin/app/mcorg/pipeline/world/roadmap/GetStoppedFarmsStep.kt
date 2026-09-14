package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure

/** A decommissioned farm, and how much of what it made no farm covers any more. */
data class StoppedFarm(val projectId: Int, val name: String, val uncoveredItems: Long)

/**
 * Every decommissioned project in [worldId] (MCO-541), with what stopping it costs the plans still
 * open.
 *
 * The roadmap's edges drop a decommissioned farm — rightly, nothing waits on a farm that will not
 * be restarted — and that left the graph view with no trace of it at all. Decommissioning Forever
 * world's two largest farms nearly tripled the hand list (60,454 → 178,181) on a page that never
 * said why.
 *
 * [StoppedFarm.uncoveredItems] is the demand of unfinished projects for the items the farm made,
 * where nothing supplies them now: they are gathered or crafted by hand again. An item another
 * running farm also makes is still `SUPPLIED`, and does not count — stopping this farm cost
 * nothing there.
 */
data class GetStoppedFarmsStep(val worldId: Int) : Step<Unit, AppFailure.DatabaseError, List<StoppedFarm>> {

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<StoppedFarm>> =
        DatabaseSteps.query<Unit, List<StoppedFarm>>(
            sql = SafeSQL.select(
                """
                SELECT
                  p.id,
                  p.name,
                  COALESCE((
                    SELECT SUM(d.quantity)
                    FROM project_demand d
                    JOIN projects c ON c.id = d.project_id
                    WHERE c.world_id = p.world_id
                      AND c.state NOT IN (?, ?, ?, ?)
                      AND d.node_status <> 'SUPPLIED'
                      AND d.item_id IN (SELECT pp.item_id FROM project_productions pp WHERE pp.project_id = p.id)
                  ), 0) AS uncovered
                FROM projects p
                WHERE p.world_id = ?
                  AND p.state = ?
                ORDER BY uncovered DESC, p.name
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setString(1, ProjectState.DONE.name)
                statement.setString(2, ProjectState.CANCELLED.name)
                statement.setString(3, ProjectState.ARCHIVED.name)
                statement.setString(4, ProjectState.DECOMMISSIONED.name)
                statement.setInt(5, worldId)
                statement.setString(6, ProjectState.DECOMMISSIONED.name)
            },
            resultMapper = { rs ->
                buildList {
                    while (rs.next()) {
                        add(StoppedFarm(rs.getInt("id"), rs.getString("name"), rs.getLong("uncovered")))
                    }
                }
            },
        ).process(Unit)
}
