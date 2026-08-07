package app.mcorg.pipeline.resources

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

/**
 * Input for [GetWorldFarmSuppliesStep].
 *
 * @param worldId the world whose operational projects' productions are collected.
 * @param excludeProjectId the project being planned — its own productions never
 *   supply its own plan (a farm's build materials must not be satisfied by the
 *   farm itself).
 */
data class WorldFarmSuppliesInput(
    val worldId: Int,
    val excludeProjectId: Int,
)

/** One produced item of an operational project in the world. */
data class FarmSupplyRow(
    val itemId: String,
    val projectId: Int,
    val projectName: String,
)

/**
 * Loads the produced items of all operational projects in a world (MCO-296).
 *
 * A project is operational when its lifecycle state is [ProjectState.DONE] — for a
 * project with `project_productions` rows that means "built and producing", so its
 * output supplies every other project's gathering plan. CANCELLED/ARCHIVED projects
 * are decommissioned and never supply.
 *
 * Rows are ordered by project name so callers that reduce multiple producers of the
 * same item to a single [app.mcorg.engine.plan.SupplySource.Farm] pick deterministically.
 */
val GetWorldFarmSuppliesStep = DatabaseSteps.query<WorldFarmSuppliesInput, List<FarmSupplyRow>>(
    sql = SafeSQL.select("""
                SELECT
                    pp.item_id,
                    p.id AS project_id,
                    p.name AS project_name
                FROM project_productions pp
                JOIN projects p ON p.id = pp.project_id
                WHERE p.world_id = ?
                  AND p.state = ?
                  AND p.id <> ?
                ORDER BY p.name, pp.item_id
            """),
    parameterSetter = { statement, input ->
        statement.setInt(1, input.worldId)
        statement.setString(2, ProjectState.DONE.name)
        statement.setInt(3, input.excludeProjectId)
    },
    resultMapper = { resultSet ->
        val rows = mutableListOf<FarmSupplyRow>()
        while (resultSet.next()) {
            rows.add(
                FarmSupplyRow(
                    itemId = resultSet.getString("item_id"),
                    projectId = resultSet.getInt("project_id"),
                    projectName = resultSet.getString("project_name"),
                )
            )
        }
        rows
    }
)
