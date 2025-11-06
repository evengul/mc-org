package app.mcorg.pipeline.project.dependencies

import app.mcorg.domain.model.project.NamedProjectId
import app.mcorg.domain.model.project.ProjectDependency
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.addDependencyForm
import app.mcorg.presentation.templated.project.dependenciesList
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleCreateProjectDependency() {
    val user = this.getUser()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val parameters = this.receiveParameters()

    executePipeline(
        onSuccess = { (dependencies, availableDependencies) ->
            respondHtml(createHTML().div {
                dependenciesList(worldId, projectId, dependencies)
            } + createHTML().form {
                hxOutOfBands("true")
                addDependencyForm(user, worldId, projectId, availableDependencies)
            })
        },
        onFailure = {
            respond(HttpStatusCode.InternalServerError, "Failed to create project dependency")
        }
    ) {
        value(parameters)
            .step(ValidateProjectDependencyInputStep)
            .step(EnsureNoDependencyLoopDetectedStep(projectId))
            .step(CreateProjectDependencyStep(projectId))
            .value(Unit)
            .step(GetProjectDependenciesStep(projectId))
            .step(object : Step<List<ProjectDependency>, AppFailure, Pair<List<ProjectDependency>, List<NamedProjectId>>> {
                override suspend fun process(input: List<ProjectDependency>): Result<AppFailure, Pair<List<ProjectDependency>, List<NamedProjectId>>> {
                    return GetAvailableProjectDependenciesStep(worldId).process(projectId)
                        .map { input to it }
                }
            })
    }
}

private object ValidateProjectDependencyInputStep : Step<Parameters, AppFailure.ValidationError, Int> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Int> {
        return ValidationSteps.requiredInt(
            parameterName = "dependencyProjectId",
            errorMapper = { AppFailure.ValidationError(listOf(it)) }
        ).process(input)
    }
}

data class EnsureNoDependencyLoopDetectedStep(val projectId: Int) : Step<Int, AppFailure, Int> {
    override suspend fun process(input: Int): Result<AppFailure, Int> {
        val result = DatabaseSteps.query<Int, Boolean>(
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
            resultMapper = {
                if (it.next()) {
                    it.getBoolean("loop_detected")
                } else {
                    false
                }
            }
        ).process(input)

        return when (result) {
            is Result.Failure -> result
            is Result.Success -> {
                when (result.value) {
                    true -> Result.failure(AppFailure.customValidationError("dependencyProjectId",  "Adding this dependency would create a dependency loop"))
                    false -> Result.success(input)
                }
            }}
    }
}

private data class CreateProjectDependencyStep(val projectId: Int): Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Int>(
            SafeSQL.insert("INSERT INTO project_dependencies (project_id, depends_on_project_id) VALUES (?, ?)"),
            parameterSetter = { statement, dependencyProjectId ->
                statement.setInt(1, projectId)
                statement.setInt(2, dependencyProjectId)
            }
        ).process(input)
    }
}

