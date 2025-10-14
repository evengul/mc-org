package app.mcorg.pipeline.project.dependencies

import app.mcorg.domain.model.project.ProjectDependency
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.project.toDependencies

data class GetProjectDependenciesStep(val projectId: Int) : Step<Unit, DatabaseFailure, List<ProjectDependency>> {
    override suspend fun process(input: Unit): Result<DatabaseFailure, List<ProjectDependency>> {
        return DatabaseSteps.query<Unit, DatabaseFailure, List<ProjectDependency>>(
            SafeSQL.select("""
                SELECT 
                    p1.id as dependent_id,
                    p1.name as dependent_name,
                    p1.stage as dependent_stage,
                    p2.id as dependency_id,
                    p2.name as dependency_name,
                    p2.stage as dependency_stage
                FROM project_dependencies pd
                JOIN projects p1 ON pd.project_id = p1.id
                JOIN projects p2 ON pd.depends_on_project_id = p2.id
                WHERE pd.project_id = ?
                ORDER BY p2.name
            """.trimIndent()),
            parameterSetter = { statement, _ ->
                statement.setInt(1, projectId)
            },
            errorMapper = { it },
            resultMapper = { it.toDependencies() }
        ).process(input)
    }
}