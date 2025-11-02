package app.mcorg.pipeline.project.dependencies

import app.mcorg.domain.model.project.NamedProjectId
import app.mcorg.domain.model.project.ProjectDependency
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.addDependencyForm
import app.mcorg.presentation.templated.project.dependenciesList
import app.mcorg.presentation.utils.getProjectDependencyId
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.stream.createHTML

sealed interface DeleteProjectDependencyFailure {
    object DatabaseError : DeleteProjectDependencyFailure
}

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
        },
        onFailure = {
            respond(HttpStatusCode.InternalServerError, "Failed to delete project dependency")
        }
    ) {
        step(Step.value(dependencyId))
            .step(DeleteProjectDependencyStep(projectId))
            .step(Step.value(Unit))
            .step(object : Step<Unit, DeleteProjectDependencyFailure.DatabaseError, List<ProjectDependency>> {
                override suspend fun process(input: Unit): Result<DeleteProjectDependencyFailure.DatabaseError, List<ProjectDependency>> {
                    return GetProjectDependenciesStep(projectId).process(input)
                        .mapError { DeleteProjectDependencyFailure.DatabaseError }
                }
            }).step(object : Step<List<ProjectDependency>, DeleteProjectDependencyFailure, Pair<List<ProjectDependency>, List<NamedProjectId>>> {
                override suspend fun process(input: List<ProjectDependency>): Result<DeleteProjectDependencyFailure, Pair<List<ProjectDependency>, List<NamedProjectId>>> {
                    return GetAvailableProjectDependenciesStep(worldId).process(projectId)
                        .mapError { DeleteProjectDependencyFailure.DatabaseError }
                        .map { availableDependencies -> Pair(input, availableDependencies) }
                }
            })
    }
}

private data class DeleteProjectDependencyStep(val projectId: Int) : Step<Int, DeleteProjectDependencyFailure, Unit> {
    override suspend fun process(input: Int): Result<DeleteProjectDependencyFailure, Unit> {
        return DatabaseSteps.update<Int, DeleteProjectDependencyFailure.DatabaseError>(
            SafeSQL.delete("DELETE FROM project_dependencies WHERE project_id = ? AND depends_on_project_id = ?"),
            parameterSetter = { statement, dependencyId ->
                statement.setInt(1, projectId)
                statement.setInt(2, dependencyId)
            },
            errorMapper = { DeleteProjectDependencyFailure.DatabaseError }
        ).process(input).map {  }
    }
}

