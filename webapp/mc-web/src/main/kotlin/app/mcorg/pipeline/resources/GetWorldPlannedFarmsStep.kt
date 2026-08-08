package app.mcorg.pipeline.resources

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

/** Input for [GetWorldPlannedFarmsStep] — mirrors [WorldFarmSuppliesInput]. */
data class WorldPlannedFarmsInput(
    val worldId: Int,
    val excludeProjectId: Int,
)

/** One produced item of a farm project that is not operational yet. */
data class PlannedFarmRow(
    val itemId: String,
    val projectId: Int,
    val projectName: String,
)

/**
 * The mirror image of [GetWorldFarmSuppliesStep] (MCO-296): produced items of farm projects
 * that are **not yet** operational — still PENDING, ACTIVE or PAUSED.
 *
 * These deliberately do not supply anything: until the farm is DONE, its output does not
 * exist, and the planner must keep telling you to gather those items by hand. What they do
 * carry is a promise worth surfacing (MCO-299) — "32 Iron Ingot will come from Iron Farm
 * once it's running" — so the manual gathering reads as a stopgap rather than the plan.
 *
 * CANCELLED and ARCHIVED farms are decommissioned and promise nothing.
 */
val GetWorldPlannedFarmsStep = DatabaseSteps.query<WorldPlannedFarmsInput, List<PlannedFarmRow>>(
    sql = SafeSQL.select("""
                SELECT
                    pp.item_id,
                    p.id AS project_id,
                    p.name AS project_name
                FROM project_productions pp
                JOIN projects p ON p.id = pp.project_id
                WHERE p.world_id = ?
                  AND p.state IN (?, ?, ?)
                  AND p.id <> ?
                ORDER BY p.name, pp.item_id
            """),
    parameterSetter = { statement, input ->
        statement.setInt(1, input.worldId)
        statement.setString(2, ProjectState.PENDING.name)
        statement.setString(3, ProjectState.ACTIVE.name)
        statement.setString(4, ProjectState.PAUSED.name)
        statement.setInt(5, input.excludeProjectId)
    },
    resultMapper = { resultSet ->
        val rows = mutableListOf<PlannedFarmRow>()
        while (resultSet.next()) {
            rows.add(
                PlannedFarmRow(
                    itemId = resultSet.getString("item_id"),
                    projectId = resultSet.getInt("project_id"),
                    projectName = resultSet.getString("project_name"),
                )
            )
        }
        rows
    }
)
