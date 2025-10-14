package app.mcorg.pipeline.project.settings

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.GetProjectByIdStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.projectSettingsForm
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.main
import kotlinx.html.stream.createHTML

data class UpdateProjectMetadataInput(
    val name: String,
    val description: String,
    val type: ProjectType
)

sealed interface UpdateProjectMetadataFailure {
    data class ValidationError(val errors: List<ValidationFailure>) : UpdateProjectMetadataFailure
    object DatabaseError : UpdateProjectMetadataFailure
}

suspend fun ApplicationCall.handleUpdateProjectMetadata() {
    val projectId = this.getProjectId()
    val parameters = this.receiveParameters()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().form {
                projectSettingsForm(it)
            } + createHTML().main {
                hxOutOfBands("innerHTML:h1")
                + it.name
            } + createHTML().div {
                hxOutOfBands("innerHTML:#project-type")
                + "${it.type.toPrettyEnumName()} Project"
            } + createHTML().div {
                hxOutOfBands("innerHTML:#project-description")
                + it.description
            })
        },
        onFailure = {
            respond(HttpStatusCode.InternalServerError, "Failed to update project metadata")
        }
    ) {
        step(Step.value(parameters))
            .step(ValidateUpdateProjectMetadataInputStep)
            .step(UpdateProjectMetadataStep(projectId))
            .step(GetUpdatedProjectStep(projectId))
    }
}

private object ValidateUpdateProjectMetadataInputStep : Step<Parameters, UpdateProjectMetadataFailure.ValidationError, UpdateProjectMetadataInput> {
    override suspend fun process(input: Parameters): Result<UpdateProjectMetadataFailure.ValidationError, UpdateProjectMetadataInput> {
        val name = ValidationSteps.required("name") { it }
            .process(input)
            .flatMap { existingName -> ValidationSteps.validateLength("name", 3, 100) { it }.process(existingName) }

        val description = input["description"]?.takeIf { it.isNotBlank() }?.let { existingDescription ->
            ValidationSteps.validateLength("description", 3, 100) { it }.process(existingDescription)
        }

        val type = ValidationSteps.required("type") { it }
            .process(input)
            .flatMap { existingType -> ValidationSteps.validateAllowedValues("type", ProjectType.entries.map { it.toString() }, { it }, false).process(existingType) }
            .map { ProjectType.valueOf(it) }

        val errors = listOfNotNull(
            name.errorOrNull(),
            description?.errorOrNull(),
            type.errorOrNull()
        )

        return if (errors.isNotEmpty()) {
            Result.Failure(UpdateProjectMetadataFailure.ValidationError(errors))
        } else {
            Result.Success(
                UpdateProjectMetadataInput(
                    name = name.getOrNull()!!,
                    description = description?.getOrNull() ?: "",
                    type = type.getOrNull()!!
                )
            )
        }
    }
}

private data class UpdateProjectMetadataStep(val projectId: Int) : Step<UpdateProjectMetadataInput, UpdateProjectMetadataFailure.DatabaseError, Unit> {
    override suspend fun process(input: UpdateProjectMetadataInput): Result<UpdateProjectMetadataFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<UpdateProjectMetadataInput, UpdateProjectMetadataFailure.DatabaseError>(
            SafeSQL.update("UPDATE projects SET name = ?, description = ?, type = ? WHERE id = ?"),
            parameterSetter = { statement, metadata ->
                statement.setString(1, metadata.name)
                statement.setString(2, metadata.description)
                statement.setString(3, metadata.type.name)
                statement.setInt(4, projectId)
            },
            errorMapper = { UpdateProjectMetadataFailure.DatabaseError }
        ).process(input).map {  }
    }
}

private data class GetUpdatedProjectStep(val projectId: Int) : Step<Unit, UpdateProjectMetadataFailure.DatabaseError, Project> {
    override suspend fun process(input: Unit): Result<UpdateProjectMetadataFailure.DatabaseError, Project> {
        return GetProjectByIdStep.process(projectId)
            .mapError { UpdateProjectMetadataFailure.DatabaseError }
    }
}