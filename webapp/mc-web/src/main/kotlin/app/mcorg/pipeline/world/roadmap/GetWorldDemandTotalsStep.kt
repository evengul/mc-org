package app.mcorg.pipeline.world.roadmap

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure

/**
 * Each project's planned demand in [worldId] — from farms plus by hand — by project id.
 *
 * What [RoadmapGraphLayout.terminalsOf] ranks final projects by, and how it finds the ones no farm
 * feeds: a project with nothing but hand work has no supply line to sum, so the roadmap's edges
 * alone cannot see it. Intermediates (`RESOLVED`, crafted on the way) are left out; every one of
 * them resolves down to rows that are counted.
 *
 * One query for the whole world rather than one per project, because every unfinished project is a
 * candidate. Decorates the page: a failure degrades to ranking by supply lines, as before.
 */
data class GetWorldDemandTotalsStep(val worldId: Int) : Step<Unit, AppFailure.DatabaseError, Map<Int, Long>> {

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, Map<Int, Long>> =
        DatabaseSteps.query<Unit, Map<Int, Long>>(
            sql = SafeSQL.select(
                """
                SELECT d.project_id, SUM(d.quantity) AS total
                FROM project_demand d
                JOIN projects p ON p.id = d.project_id
                WHERE p.world_id = ?
                  AND d.node_status IN ('SUPPLIED', 'RAW_GATHER')
                GROUP BY d.project_id
                """.trimIndent()
            ),
            parameterSetter = { statement, _ -> statement.setInt(1, worldId) },
            resultMapper = { rs ->
                buildMap {
                    while (rs.next()) put(rs.getInt("project_id"), rs.getLong("total"))
                }
            },
        ).process(Unit)
}
