package app.mcorg.pipeline.project.resources

import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.domain.model.project.ProjectProductionMode
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import java.sql.ResultSet

/**
 * Every production row a project has: its one mode-less list, or each runtime mode's rates
 * (MCO-413). All of them supply once the project is Done (MCO-588), so an item two modes make
 * appears twice here — once per mode, with that mode's rate. Readers asking only *which items* a
 * project supplies read the `project_supplied_items` view instead.
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
                FROM project_productions
                WHERE project_id = ?
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

/** The ways a farm can be run, in the design's order. Empty for most projects. */
val GetProjectProductionModesStep = DatabaseSteps.query<Int, List<ProjectProductionMode>>(
    sql = SafeSQL.select("""
                SELECT id, project_id, name, position
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
                )
            )
        }
        modes
    }
)
