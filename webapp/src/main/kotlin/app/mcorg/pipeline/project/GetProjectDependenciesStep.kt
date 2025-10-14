package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectDependency
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import java.sql.ResultSet

data class GetProjectDependenciesInput(
    val projectId: Int
)

data class ProjectDependencyData(
    val dependencies: List<ProjectDependency>, // ProjectDependencies this project depends on
    val dependents: List<ProjectDependency>    // ProjectDependencies that depend on this project
)

sealed interface GetProjectDependenciesFailures {
    data object DatabaseError : GetProjectDependenciesFailures
}

object GetProjectDependenciesStep : Step<GetProjectDependenciesInput, GetProjectDependenciesFailures, ProjectDependencyData> {
    override suspend fun process(input: GetProjectDependenciesInput): Result<GetProjectDependenciesFailures, ProjectDependencyData> {
        // First fetch projects this project depends on
        val dependenciesStep = DatabaseSteps.query<GetProjectDependenciesInput, GetProjectDependenciesFailures, List<ProjectDependency>>(
            sql = SafeSQL.select("""
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
            """),
            parameterSetter = { statement, queryInput ->
                statement.setInt(1, queryInput.projectId)
            },
            errorMapper = { GetProjectDependenciesFailures.DatabaseError },
            resultMapper = { it.toDependencies() }
        )

        // Then fetch projects that depend on this project
        val dependentsStep = DatabaseSteps.query<GetProjectDependenciesInput, GetProjectDependenciesFailures, List<ProjectDependency>>(
            sql = SafeSQL.select("""
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
                WHERE pd.depends_on_project_id = ?
                ORDER BY p1.name
            """),
            parameterSetter = { statement, queryInput ->
                statement.setInt(1, queryInput.projectId)
            },
            errorMapper = { GetProjectDependenciesFailures.DatabaseError },
            resultMapper = { it.toDependencies() }
        )

        val dependenciesResult = dependenciesStep.process(input)
        if (dependenciesResult is Result.Failure) {
            return dependenciesResult
        }

        val dependentsResult = dependentsStep.process(input)
        if (dependentsResult is Result.Failure) {
            return dependentsResult
        }

        return Result.success(
            ProjectDependencyData(
                dependencies = dependenciesResult.getOrNull()!!,
                dependents = dependentsResult.getOrNull()!!
            )
        )
    }
}

fun ResultSet.toDependencies() = buildList {
    while (next()) {
        add(
            ProjectDependency(
                dependentId = getInt("dependent_id"),
                dependentName = getString("dependent_name"),
                dependentStage = app.mcorg.domain.model.project.ProjectStage.valueOf(getString("dependent_stage")),
                dependencyId = getInt("dependency_id"),
                dependencyName = getString("dependency_name"),
                dependencyStage = app.mcorg.domain.model.project.ProjectStage.valueOf(getString("dependency_stage"))
            )
        )
    }
}.toList()
