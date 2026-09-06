package app.mcorg.pipeline.world.roadmap.ordering

import app.mcorg.domain.model.world.ManualOrdering
import app.mcorg.domain.model.world.OrderingSource
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

/**
 * Every hand-made ordering in a world, for the roadmap's roster (MCO-302).
 *
 * The world is reached through the *dependent* project, which is the same join
 * [app.mcorg.pipeline.project.commonsteps.GetProjectEdgesStep] uses — both ends of an edge
 * are in the same world, so either would do, and using the same one keeps the roster and the
 * graph counting the same rows.
 *
 * Ordered by the prerequisite's name so the roster reads in the direction the rows do.
 */
data class GetManualOrderingsStep(val worldId: Int) :
    Step<Unit, AppFailure.DatabaseError, List<ManualOrdering>> {

    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<ManualOrdering>> {
        return DatabaseSteps.query<Unit, List<ManualOrdering>>(
            sql = SafeSQL.select("""
                SELECT
                  pd.id                    AS id,
                  pd.depends_on_project_id AS first_id,
                  pf.name                  AS first_name,
                  pd.project_id            AS then_id,
                  pt.name                  AS then_name,
                  pd.reason                AS reason,
                  pd.declared_by           AS declared_by
                FROM project_dependencies pd
                JOIN projects pt ON pt.id = pd.project_id
                JOIN projects pf ON pf.id = pd.depends_on_project_id
                WHERE pt.world_id = ?
                ORDER BY pf.name, pt.name
            """.trimIndent()),
            parameterSetter = { statement, _ -> statement.setInt(1, worldId) },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            ManualOrdering(
                                id = resultSet.getInt("id"),
                                firstProjectId = resultSet.getInt("first_id"),
                                firstProjectName = resultSet.getString("first_name"),
                                thenProjectId = resultSet.getInt("then_id"),
                                thenProjectName = resultSet.getString("then_name"),
                                reason = resultSet.getString("reason")?.takeIf { it.isNotBlank() },
                                declaredBy = OrderingSource.fromDb(resultSet.getString("declared_by")),
                            )
                        )
                    }
                }
            }
        ).process(input)
    }
}
