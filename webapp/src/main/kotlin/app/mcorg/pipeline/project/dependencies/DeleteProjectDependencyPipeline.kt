package app.mcorg.pipeline.project.dependencies

import app.mcorg.domain.model.project.NamedProjectId
import app.mcorg.domain.model.project.ProjectDependency
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.addDependencyForm
import app.mcorg.presentation.templated.project.dependenciesList
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleDeleteProjectDependency() {
    val user = this.getUser()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val dependencyId = this.getProjectDependencyId()

    executePipeline(
        onSuccess = { (dependencies, availableDependencies) ->
            respondHtml(createHTML().div {
                dependenciesList(worldId, projectId, dependencies)
            } + createHTML().form {
                hxOutOfBands("true")
                addDependencyForm(user, worldId, projectId, availableDependencies)
            })
        }
    ) {
        step(Step.value(dependencyId))
            .step(DeleteProjectDependencyStep(projectId))
            .value(Unit)
            .step(GetProjectDependenciesStep(projectId))
            .step(object : Step<List<ProjectDependency>, AppFailure, Pair<List<ProjectDependency>, List<NamedProjectId>>> {
                override suspend fun process(input: List<ProjectDependency>): Result<AppFailure, Pair<List<ProjectDependency>, List<NamedProjectId>>> {
                    return GetAvailableProjectDependenciesStep(worldId).process(projectId)
                        .map { availableDependencies -> Pair(input, availableDependencies) }
                }
            })
    }
}

private data class DeleteProjectDependencyStep(val projectId: Int) : Step<Int, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<Int>(
            SafeSQL.delete("DELETE FROM project_dependencies WHERE project_id = ? AND depends_on_project_id = ?"),
            parameterSetter = { statement, dependencyId ->
                statement.setInt(1, projectId)
                statement.setInt(2, dependencyId)
            }
        ).process(input).map {  }
    }
}

