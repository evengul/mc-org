package app.mcorg.pipeline.project.settings

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
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.li
import kotlinx.html.main
import kotlinx.html.stream.createHTML

sealed interface UpdateProjectNameFailure {
    data class ValidationError(val errors: List<ValidationFailure>) : UpdateProjectNameFailure
    object DatabaseError : UpdateProjectNameFailure
}

suspend fun ApplicationCall.handleUpdateProjectName() {
    val projectId = this.getProjectId()
    val parameters = this.receiveParameters()

    executePipeline(
        onSuccess = { respondHtml(createHTML().li {
            createAlert(
                id = "project-name-updated-success-alert",
                type = AlertType.SUCCESS,
                title = "Project Name Updated",
            )
        } + createHTML().main {
            hxOutOfBands("innerHTML:.project-header h1")
            + it
        }) },
        onFailure = {
            respondHtml(createHTML().li {
                createAlert(
                    id = "project-name-updated-success-alert",
                    type = AlertType.ERROR,
                    title = "Failed to update project name",
                    message = when(it) {
                        is UpdateProjectNameFailure.ValidationError ->
                            "Validation failed: ${it.errors.joinToString { error -> error.toString() }}"
                        is UpdateProjectNameFailure.DatabaseError ->
                            "An unexpected database error occurred"
                    }
                )
            })
        }
    ) {
        step(Step.value(parameters))
            .step(ValidateProjectNameInputStep)
            .step(UpdateProjectNameStep(projectId))
    }
}

private object ValidateProjectNameInputStep : Step<Parameters, UpdateProjectNameFailure.ValidationError, String> {
    override suspend fun process(input: Parameters): Result<UpdateProjectNameFailure.ValidationError, String> {
        val name = ValidationSteps.required("name") { it }
            .process(input)
            .flatMap { existingName -> ValidationSteps.validateLength("name", 3, 100) { it }.process(existingName) }

        return when(name) {
            is Result.Success -> Result.success(name.value)
            is Result.Failure -> Result.failure(UpdateProjectNameFailure.ValidationError(listOf(name.error)))
        }
    }
}

private data class UpdateProjectNameStep(val projectId: Int) : Step<String, UpdateProjectNameFailure.DatabaseError, String> {
    override suspend fun process(input: String): Result<UpdateProjectNameFailure.DatabaseError, String> {
        return DatabaseSteps.update<String, UpdateProjectNameFailure.DatabaseError>(
            sql = SafeSQL.update("UPDATE projects SET name = ? WHERE id = ?"),
            parameterSetter = { statement, name ->
                statement.setString(1, name)
                statement.setInt(2, projectId)
            },
            errorMapper = { UpdateProjectNameFailure.DatabaseError }
        ).process(input).map { input }
    }
}