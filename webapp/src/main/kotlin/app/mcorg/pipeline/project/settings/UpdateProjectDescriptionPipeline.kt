package app.mcorg.pipeline.project.settings

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.layout.alert.AlertType
import app.mcorg.presentation.templated.layout.alert.createAlert
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.div
import kotlinx.html.li
import kotlinx.html.stream.createHTML

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
                        is AppFailure.ValidationError ->
                            "Validation failed: ${it.errors.joinToString { error -> error.toString() }}"
                        else ->
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

private object ValidateProjectDescriptionInputStep : Step<Parameters, AppFailure.ValidationError, String?> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, String?> {
        val description = input["description"]?.takeIf { it.isNotBlank() }?.let { existingDescription ->
            ValidationSteps.validateLength("description", 3, 100) { it }.process(existingDescription)
        }

        return when(description) {
            null -> Result.success(null)
            is Result.Success -> Result.success(description.value)
            is Result.Failure -> Result.failure(AppFailure.ValidationError(listOf(description.error)))
        }
    }
}

private data class UpdateProjectDescriptionStep(val projectId: Int) : Step<String?, AppFailure.DatabaseError, String?> {
    override suspend fun process(input: String?): Result<AppFailure.DatabaseError, String?> {
        return DatabaseSteps.update<String?>(
            sql = SafeSQL.update("UPDATE projects SET description = ? WHERE id = ?"),
            parameterSetter = { statement, name ->
                if (name == null) {
                    statement.setNull(1, java.sql.Types.VARCHAR)
                } else {
                    statement.setString(1, name)
                }
                statement.setInt(2, projectId)
            }
        ).process(input).map { input }
    }
}