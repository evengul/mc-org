package app.mcorg.pipeline.project.resources

import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

val GetResourceProductionStep = DatabaseSteps.query<Int, List<ProjectProduction>>(
    sql = SafeSQL.select("""
                SELECT 
                    id,
                    project_id,
                    name,
                    rate_per_hour
                FROM project_productions
                WHERE project_id = ?
                ORDER BY name
            """),
    parameterSetter = { statement, projectId ->
        statement.setInt(1, projectId)
    },
    resultMapper = { resultSet ->
        val productions = mutableListOf<ProjectProduction>()
        while (resultSet.next()) {
            productions.add(
                ProjectProduction(
                    id = resultSet.getInt("id"),
                    projectId = resultSet.getInt("project_id"),
                    name = resultSet.getString("name"),
                    ratePerHour = resultSet.getInt("rate_per_hour")
                )
            )
        }
        productions
    }
)