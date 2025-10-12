package app.mcorg.pipeline.project.dependencies

import app.mcorg.domain.model.project.ProjectDependency
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.toDependencies
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.project.dependenciesList
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import kotlinx.html.div
import kotlinx.html.stream.createHTML

sealed interface CreateProjectDependencyFailure {
    object DependencyLoopDetected : CreateProjectDependencyFailure
    object DatabaseError : CreateProjectDependencyFailure
    data class ValidationError(val errors: List<ValidationFailure>) : CreateProjectDependencyFailure
}

suspend fun ApplicationCall.handleCreateProjectDependency() {
    val projectId = this.getProjectId()
    val parameters = this.receiveParameters()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().div {
                dependenciesList(it)
            })
        },
        onFailure = {
            respond(HttpStatusCode.InternalServerError, "Failed to create project dependency")
        }
    ) {
        step(Step.value(parameters))
            .step(ValidateProjectDependencyInputStep)
            .step(EnsureNoDependencyLoopDetectedStep(projectId))
            .step(CreateProjectDependencyStep(projectId))
            .step(Step.value(Unit))
            .step(GetProjectDependenciesStep(projectId))
    }
}

private object ValidateProjectDependencyInputStep : Step<Parameters, CreateProjectDependencyFailure.ValidationError, Int> {
    override suspend fun process(input: Parameters): Result<CreateProjectDependencyFailure.ValidationError, Int> {
        return ValidationSteps.requiredInt(
            parameterName = "dependencyProjectId",
            errorMapper = { CreateProjectDependencyFailure.ValidationError(listOf(it)) }
        ).process(input)
    }
}

data class EnsureNoDependencyLoopDetectedStep(val projectId: Int) : Step<Int, CreateProjectDependencyFailure, Int> {
    override suspend fun process(input: Int): Result<CreateProjectDependencyFailure, Int> {
        return DatabaseSteps.query<Int, CreateProjectDependencyFailure, Boolean>(
            SafeSQL.with(
                """
                WITH RECURSIVE dependency_chain AS (
                    -- Base case: direct dependencies of the project we want to add a dependency to
                    SELECT depends_on_project_id as project_id
                    FROM project_dependencies
                    WHERE project_id = ?
                    
                    UNION
                    
                    -- Recursive case: transitive dependencies
                    SELECT pd.depends_on_project_id
                    FROM project_dependencies pd
                    INNER JOIN dependency_chain dc ON pd.project_id = dc.project_id
                ),
                reverse_dependency_chain AS (
                    -- Base case: projects that directly depend on the input project (the one we want to depend on)
                    SELECT project_id
                    FROM project_dependencies
                    WHERE depends_on_project_id = ?
                    
                    UNION
                    
                    -- Recursive case: projects that transitively depend on the input project
                    SELECT pd.project_id
                    FROM project_dependencies pd
                    INNER JOIN reverse_dependency_chain rdc ON pd.depends_on_project_id = rdc.project_id
                )
                SELECT 
                    CASE 
                        WHEN EXISTS (
                            SELECT 1 FROM dependency_chain WHERE project_id = ?
                        ) OR EXISTS (
                            SELECT 1 FROM reverse_dependency_chain WHERE project_id = ?
                        ) OR ? = ?
                        THEN true
                        ELSE false
                    END as loop_detected
                """.trimIndent()
            ),
            parameterSetter = { statement, dependencyProjectId ->
                statement.setInt(1, projectId)           // dependency_chain CTE - find what projectId depends on
                statement.setInt(2, dependencyProjectId) // reverse_dependency_chain CTE - find what depends on dependencyProjectId
                statement.setInt(3, dependencyProjectId) // check if dependencyProjectId is in projectId's dependency chain
                statement.setInt(4, projectId)           // check if projectId is in dependencyProjectId's reverse dependency chain
                statement.setInt(5, projectId)           // self-reference check: projectId
                statement.setInt(6, dependencyProjectId) // self-reference check: dependencyProjectId
            },
            errorMapper = { CreateProjectDependencyFailure.DatabaseError },
            resultMapper = {
                if (it.next()) {
                    it.getBoolean("loop_detected")
                } else {
                    false
                }
            }
        ).process(input)
            .flatMap { loopDetected ->
                when (loopDetected) {
                    true -> Result.failure(CreateProjectDependencyFailure.DependencyLoopDetected)
                    false -> Result.success(input)
                }
            }
    }
}

private data class CreateProjectDependencyStep(val projectId: Int): Step<Int, CreateProjectDependencyFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<CreateProjectDependencyFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Int, CreateProjectDependencyFailure.DatabaseError>(
            SafeSQL.insert("INSERT INTO project_dependencies (project_id, depends_on_project_id) VALUES (?, ?)"),
            parameterSetter = { statement, dependencyProjectId ->
                statement.setInt(1, projectId)
                statement.setInt(2, dependencyProjectId)
            },
            errorMapper = { CreateProjectDependencyFailure.DatabaseError }
        ).process(input)
    }
}

private data class GetProjectDependenciesStep(val projectId: Int) : Step<Unit, CreateProjectDependencyFailure.DatabaseError, List<ProjectDependency>> {
    override suspend fun process(input: Unit): Result<CreateProjectDependencyFailure.DatabaseError, List<ProjectDependency>> {
        return DatabaseSteps.query<Unit, CreateProjectDependencyFailure.DatabaseError, List<ProjectDependency>>(
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
            errorMapper = { CreateProjectDependencyFailure.DatabaseError },
            resultMapper = { it.toDependencies() }
        ).process(input)
    }
}