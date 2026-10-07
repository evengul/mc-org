package app.mcorg.pipeline.project.resources

import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.domain.model.project.ProjectProductionMode
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import java.sql.ResultSet

/**
 * What a project produces right now: its mode-less list, or its active mode's rates (MCO-413).
 * An inactive mode's items are what the farm *could* make, which is [GetModeProductionsStep]'s
 * question, not this one's.
 */
val GetResourceProductionStep = DatabaseSteps.query<Int, List<ProjectProduction>>(
    sql = SafeSQL.select("""
                SELECT
                    id,
                    project_id,
                    item_id,
                    name,
                    rate_per_hour,
                    mode_id
                FROM active_project_productions
                WHERE project_id = ?
                ORDER BY name
            """),
    parameterSetter = { statement, projectId ->
        statement.setInt(1, projectId)
    },
    resultMapper = { it.toProjectProductions() }
)

/** Every runtime mode's rates on a project, running or not — what each mode would make. */
val GetModeProductionsStep = DatabaseSteps.query<Int, List<ProjectProduction>>(
    sql = SafeSQL.select("""
                SELECT id, project_id, item_id, name, rate_per_hour, mode_id
                FROM project_productions
                WHERE project_id = ?
                  AND mode_id IS NOT NULL
                ORDER BY name
            """),
    parameterSetter = { statement, projectId ->
        statement.setInt(1, projectId)
    },
    resultMapper = { it.toProjectProductions() }
)

private fun ResultSet.toProjectProductions(): List<ProjectProduction> {
    val productions = mutableListOf<ProjectProduction>()
    while (next()) {
        productions.add(
            ProjectProduction(
                id = getInt("id"),
                projectId = getInt("project_id"),
                name = getString("name"),
                ratePerHour = getInt("rate_per_hour"),
                itemId = getString("item_id"),
                modeId = getInt("mode_id").takeUnless { wasNull() },
            )
        )
    }
    return productions
}

/** The runtime modes a project can be switched between, in the design's order. Empty for most. */
val GetProjectProductionModesStep = DatabaseSteps.query<Int, List<ProjectProductionMode>>(
    sql = SafeSQL.select("""
                SELECT id, project_id, name, position, active
                FROM project_production_modes
                WHERE project_id = ?
                ORDER BY position, name
            """),
    parameterSetter = { statement, projectId ->
        statement.setInt(1, projectId)
    },
    resultMapper = { resultSet ->
        val modes = mutableListOf<ProjectProductionMode>()
        while (resultSet.next()) {
            modes.add(
                ProjectProductionMode(
                    id = resultSet.getInt("id"),
                    projectId = resultSet.getInt("project_id"),
                    name = resultSet.getString("name"),
                    position = resultSet.getInt("position"),
                    active = resultSet.getBoolean("active"),
                )
            )
        }
        modes
    }
)
