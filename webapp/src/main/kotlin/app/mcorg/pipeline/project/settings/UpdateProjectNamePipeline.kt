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
import kotlinx.html.li
import kotlinx.html.main
import kotlinx.html.stream.createHTML

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
    ) {
        step(Step.value(parameters))
            .step(ValidateProjectNameInputStep)
            .step(UpdateProjectNameStep(projectId))
    }
}

private object ValidateProjectNameInputStep : Step<Parameters, AppFailure.ValidationError, String> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, String> {
        val name = ValidationSteps.required("name") { it }
            .process(input)
            .flatMap { existingName -> ValidationSteps.validateLength("name", 3, 100) { it }.process(existingName) }

        return when(name) {
            is Result.Success -> Result.success(name.value)
            is Result.Failure -> Result.failure(AppFailure.ValidationError(listOf(name.error)))
        }
    }
}

private data class UpdateProjectNameStep(val projectId: Int) : Step<String, AppFailure.DatabaseError, String> {
    override suspend fun process(input: String): Result<AppFailure.DatabaseError, String> {
        return DatabaseSteps.update<String>(
            sql = SafeSQL.update("UPDATE projects SET name = ? WHERE id = ?"),
            parameterSetter = { statement, name ->
                statement.setString(1, name)
                statement.setInt(2, projectId)
            }
        ).process(input).map { input }
    }
}