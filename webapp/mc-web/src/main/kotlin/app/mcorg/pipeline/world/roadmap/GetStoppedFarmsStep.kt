package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure

/**
 * A decommissioned farm, and how much of the drawn final projects' hand lists only it would cover.
 *
 * [uncoveredItems] is null when it could not be measured — the final projects' plans could not be
 * derived — which the page shows as no number rather than as zero.
 */
data class StoppedFarm(val projectId: Int, val name: String, val uncoveredItems: Long?)

/**
 * Every decommissioned project in [worldId] (MCO-541).
 *
 * The roadmap's edges drop a decommissioned farm — rightly, nothing waits on a farm that will not
 * be restarted — and that left the graph view with no trace of it at all. Decommissioning Forever
 * world's two largest farms nearly tripled the hand list (60,454 → 178,181) on a page that never
 * said why.
 *
 * What stopping each one costs is measured by the roadmap, not here (MCO-572): it is a difference
 * between two hand lists — every farm still to build finished, with and without this one running
 * again — and those are plans, not a query. It used to be a sum of demand rows for the items the
 * farm made, which counted crafted items alongside the raw ones under them and overlapped the
 * panels' `yours either way`. So this lists the farms, and [uncoveredItems] arrives unset.
 */
data class GetStoppedFarmsStep(val worldId: Int) : Step<Unit, AppFailure.DatabaseError, List<StoppedFarm>> {

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<StoppedFarm>> =
        DatabaseSteps.query<Unit, List<StoppedFarm>>(
            sql = SafeSQL.select(
                """
                SELECT p.id, p.name
                FROM projects p
                WHERE p.world_id = ?
                  AND p.state = ?
                ORDER BY p.name
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
                statement.setString(2, ProjectState.DECOMMISSIONED.name)
            },
            resultMapper = { rs ->
                buildList {
                    while (rs.next()) add(StoppedFarm(rs.getInt("id"), rs.getString("name"), uncoveredItems = null))
                }
            },
        ).process(Unit)
}

/**
 * Every item id in each of [projectIds]' stored demand, crafted, supplied and gathered alike.
 *
 * What lets the roadmap skip a scenario that cannot change anything: a stopped farm whose output
 * appears nowhere in a final project's plan costs that project nothing, and deriving a plan to
 * prove it would cost most of a second.
 */
data class GetDemandItemIdsStep(val projectIds: Set<Int>) :
    Step<Unit, AppFailure.DatabaseError, Map<Int, Set<String>>> {

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Map<Int, Set<String>>> =
        DatabaseSteps.query<Unit, Map<Int, Set<String>>>(
            sql = SafeSQL.select("SELECT project_id, item_id FROM project_demand WHERE project_id = ANY(?)"),
            parameterSetter = { statement, _ ->
                statement.setArray(1, statement.connection.createArrayOf("integer", projectIds.toTypedArray()))
            },
            resultMapper = { rs ->
                val byProject = mutableMapOf<Int, MutableSet<String>>()
                while (rs.next()) byProject.getOrPut(rs.getInt("project_id")) { mutableSetOf() }.add(rs.getString("item_id"))
                byProject
            },
        ).process(Unit)
}

/**
 * Every project in [worldId] with anything collected or any task done — the ones the roadmap calls
 * "building" (MCO-579). A project's declared state is no guide: every import arrives ACTIVE whether
 * or not anyone has touched it.
 */
data class GetStartedProjectsStep(val worldId: Int) : Step<Unit, AppFailure.DatabaseError, Set<Int>> {

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Set<Int>> =
        DatabaseSteps.query<Unit, Set<Int>>(
            sql = SafeSQL.select(
                """
                SELECT p.id
                FROM projects p
                WHERE p.world_id = ?
                  AND (
                    EXISTS (SELECT 1 FROM resource_gathering_progress g WHERE g.project_id = p.id AND g.collected > 0)
                    OR EXISTS (SELECT 1 FROM action_task t WHERE t.project_id = p.id AND t.completed)
                  )
                """.trimIndent()
            ),
            parameterSetter = { statement, _ -> statement.setInt(1, worldId) },
            resultMapper = { rs ->
                buildSet {
                    while (rs.next()) add(rs.getInt("id"))
                }
            },
        ).process(Unit)
}
