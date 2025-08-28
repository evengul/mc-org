package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL

data class GetResourceProductionInput(
    val projectId: Int
)

sealed interface GetResourceProductionFailures {
    data object DatabaseError : GetResourceProductionFailures
}

object GetResourceProductionStep : Step<GetResourceProductionInput, GetResourceProductionFailures, List<ProjectProduction>> {
    override suspend fun process(input: GetResourceProductionInput): Result<GetResourceProductionFailures, List<ProjectProduction>> {
        return DatabaseSteps.query<GetResourceProductionInput, GetResourceProductionFailures, List<ProjectProduction>>(
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
            parameterSetter = { statement, queryInput ->
                statement.setInt(1, queryInput.projectId)
            },
            errorMapper = { GetResourceProductionFailures.DatabaseError },
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
        ).process(input)
    }
}
