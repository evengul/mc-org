package app.mcorg.pipeline.resources

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import java.sql.ResultSet

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

/**
 * Input for [GetAssumedFarmSuppliesStep]: projects to treat as producing although they are not.
 *
 * @param excludeProjectId the project being planned — as for [WorldFarmSuppliesInput], its own
 *   productions never supply its own plan, even when it is in [projectIds].
 */
data class AssumedFarmSuppliesInput(
    val projectIds: Set<Int>,
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
 * output supplies every other project's gathering plan. CANCELLED, ARCHIVED and
 * DECOMMISSIONED projects never supply (MCO-541).
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
    resultMapper = { it.toFarmSupplyRows() }
)

/**
 * The produced items of [AssumedFarmSuppliesInput.projectIds], whatever their state (MCO-572).
 *
 * For a plan derived *as if* those farms were built — the roadmap's "promised" split. The rows are
 * shaped like [GetWorldFarmSuppliesStep]'s so the same fold turns them into supply, and ordered
 * the same way so that fold picks deterministically.
 */
val GetAssumedFarmSuppliesStep = DatabaseSteps.query<AssumedFarmSuppliesInput, List<FarmSupplyRow>>(
    sql = SafeSQL.select("""
                SELECT
                    pp.item_id,
                    p.id AS project_id,
                    p.name AS project_name
                FROM project_productions pp
                JOIN projects p ON p.id = pp.project_id
                WHERE p.id = ANY(?)
                  AND p.id <> ?
                ORDER BY p.name, pp.item_id
            """),
    parameterSetter = { statement, input ->
        statement.setArray(1, statement.connection.createArrayOf("integer", input.projectIds.toTypedArray()))
        statement.setInt(2, input.excludeProjectId)
    },
    resultMapper = { it.toFarmSupplyRows() }
)

private fun ResultSet.toFarmSupplyRows(): List<FarmSupplyRow> {
    val rows = mutableListOf<FarmSupplyRow>()
    while (next()) {
        rows.add(
            FarmSupplyRow(
                itemId = getString("item_id"),
                projectId = getInt("project_id"),
                projectName = getString("project_name"),
            )
        )
    }
    return rows
}
