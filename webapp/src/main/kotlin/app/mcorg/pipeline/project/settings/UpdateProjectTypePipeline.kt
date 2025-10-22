package app.mcorg.pipeline.project.settings

import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.layout.alert.AlertType
import app.mcorg.presentation.templated.layout.alert.createAlert
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.li
import kotlinx.html.stream.createHTML

sealed interface UpdateProjectTypeFailure {
    data class ValidationError(val errors: List<ValidationFailure>) : UpdateProjectTypeFailure
    object DatabaseError : UpdateProjectTypeFailure
}

suspend fun ApplicationCall.handleUpdateProjectType() {
    val projectId = this.getProjectId()
    val parameters = this.receiveParameters()

    executePipeline(
        onSuccess = { respondHtml(createHTML().li {
            createAlert(
                id = "project-type-updated-success-alert",
                type = AlertType.SUCCESS,
                title = "Project Type Updated",
            )
        } + createHTML().div {
            hxOutOfBands("innerHTML:#project-type")
            + "${it.toPrettyEnumName()} Project"
        }) },
        onFailure = {
            respondHtml(createHTML().li {
                createAlert(
                    id = "project-type-updated-failure-alert",
                    type = AlertType.ERROR,
                    title = "Failed to update project type",
                    message = when(it) {
                        is UpdateProjectTypeFailure.ValidationError ->
                            "Validation failed: ${it.errors.joinToString { error -> error.toString() }}"
                        is UpdateProjectTypeFailure.DatabaseError ->
                            "An unexpected database error occurred"
                    }
                )
            })
        }
    ) {
        step(Step.value(parameters))
            .step(ValidateProjectTypeInputStep)
            .step(UpdateProjectTypeStep(projectId))
    }
}

private object ValidateProjectTypeInputStep : Step<Parameters, UpdateProjectTypeFailure.ValidationError, ProjectType> {
    override suspend fun process(input: Parameters): Result<UpdateProjectTypeFailure.ValidationError, ProjectType> {
        val type = ValidationSteps.required("type") { it }
            .process(input)
            .flatMap { existingType -> ValidationSteps.validateAllowedValues("type", ProjectType.entries.map { it.toString() }, { it }, false).process(existingType) }
            .map { ProjectType.valueOf(it) }

        return when(type) {
            is Result.Success -> Result.success(type.value)
            is Result.Failure -> Result.failure(UpdateProjectTypeFailure.ValidationError(listOf(type.error)))
        }
    }
}

private data class UpdateProjectTypeStep(val projectId: Int) : Step<ProjectType, UpdateProjectTypeFailure.DatabaseError, ProjectType> {
    override suspend fun process(input: ProjectType): Result<UpdateProjectTypeFailure.DatabaseError, ProjectType> {
        return DatabaseSteps.update<ProjectType, UpdateProjectTypeFailure.DatabaseError>(
            sql = SafeSQL.update("UPDATE projects SET type = ? WHERE id = ?"),
            parameterSetter = { statement, type ->
                statement.setString(1, type.name)
                statement.setInt(2, projectId)
            },
            errorMapper = { UpdateProjectTypeFailure.DatabaseError }
        ).process(input).map { input }
    }
}