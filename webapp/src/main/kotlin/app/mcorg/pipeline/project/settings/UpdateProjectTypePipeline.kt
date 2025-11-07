package app.mcorg.pipeline.project.settings

import app.mcorg.domain.model.project.ProjectType
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
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.div
import kotlinx.html.li
import kotlinx.html.stream.createHTML

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
        }) }
    ) {
        step(Step.value(parameters))
            .step(ValidateProjectTypeInputStep)
            .step(UpdateProjectTypeStep(projectId))
    }
}

private object ValidateProjectTypeInputStep : Step<Parameters, AppFailure.ValidationError, ProjectType> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, ProjectType> {
        val type = ValidationSteps.required("type") { it }
            .process(input)
            .flatMap { existingType -> ValidationSteps.validateAllowedValues("type", ProjectType.entries.map { it.toString() }, { it }, false).process(existingType) }
            .map { ProjectType.valueOf(it) }

        return when(type) {
            is Result.Success -> Result.success(type.value)
            is Result.Failure -> Result.failure(AppFailure.ValidationError(listOf(type.error)))
        }
    }
}

private data class UpdateProjectTypeStep(val projectId: Int) : Step<ProjectType, AppFailure.DatabaseError, ProjectType> {
    override suspend fun process(input: ProjectType): Result<AppFailure.DatabaseError, ProjectType> {
        return DatabaseSteps.update<ProjectType>(
            sql = SafeSQL.update("UPDATE projects SET type = ? WHERE id = ?"),
            parameterSetter = { statement, type ->
                statement.setString(1, type.name)
                statement.setInt(2, projectId)
            }
        ).process(input).map { input }
    }
}