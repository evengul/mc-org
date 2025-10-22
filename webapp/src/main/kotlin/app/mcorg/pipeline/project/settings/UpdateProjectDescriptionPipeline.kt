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
import kotlinx.html.div
import kotlinx.html.li
import kotlinx.html.stream.createHTML

sealed interface UpdateProjectDescriptionFailure {
    data class ValidationError(val errors: List<ValidationFailure>) : UpdateProjectDescriptionFailure
    object DatabaseError : UpdateProjectDescriptionFailure
}

suspend fun ApplicationCall.handleUpdateProjectDescription() {
    val projectId = this.getProjectId()
    val parameters = this.receiveParameters()

    executePipeline(
        onSuccess = { respondHtml(createHTML().li {
            createAlert(
                id = "project-description-updated-success-alert",
                type = AlertType.SUCCESS,
                title = "Project Description Updated",
            )
        } + if (it == null) "" else createHTML().div {
            hxOutOfBands("innerHTML:#project-description")
            + it
        }) },
        onFailure = {
            respondHtml(createHTML().li {
                createAlert(
                    id = "project-description-updated-success-alert",
                    type = AlertType.ERROR,
                    title = "Failed to update project description",
                    message = when(it) {
                        is UpdateProjectDescriptionFailure.ValidationError ->
                            "Validation failed: ${it.errors.joinToString { error -> error.toString() }}"
                        is UpdateProjectDescriptionFailure.DatabaseError ->
                            "An unexpected database error occurred"
                    }
                )
            })
        }
    ) {
        step(Step.value(parameters))
            .step(ValidateProjectDescriptionInputStep)
            .step(UpdateProjectDescriptionStep(projectId))
    }
}

private object ValidateProjectDescriptionInputStep : Step<Parameters, UpdateProjectDescriptionFailure.ValidationError, String?> {
    override suspend fun process(input: Parameters): Result<UpdateProjectDescriptionFailure.ValidationError, String?> {
        val description = input["description"]?.takeIf { it.isNotBlank() }?.let { existingDescription ->
            ValidationSteps.validateLength("description", 3, 100) { it }.process(existingDescription)
        }

        return when(description) {
            null -> Result.success(null)
            is Result.Success -> Result.success(description.value)
            is Result.Failure -> Result.failure(UpdateProjectDescriptionFailure.ValidationError(listOf(description.error)))
        }
    }
}

private data class UpdateProjectDescriptionStep(val projectId: Int) : Step<String?, UpdateProjectDescriptionFailure.DatabaseError, String?> {
    override suspend fun process(input: String?): Result<UpdateProjectDescriptionFailure.DatabaseError, String?> {
        return DatabaseSteps.update<String?, UpdateProjectDescriptionFailure.DatabaseError>(
            sql = SafeSQL.update("UPDATE projects SET description = ? WHERE id = ?"),
            parameterSetter = { statement, name ->
                if (name == null) {
                    statement.setNull(1, java.sql.Types.VARCHAR)
                } else {
                    statement.setString(1, name)
                }
                statement.setInt(2, projectId)
            },
            errorMapper = { UpdateProjectDescriptionFailure.DatabaseError }
        ).process(input).map { input }
    }
}