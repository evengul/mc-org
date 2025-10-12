package app.mcorg.pipeline.project.resources

import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.project.projectResourceProductionItem
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import kotlinx.html.li
import kotlinx.html.stream.createHTML

data class CreateProjectProductionInput(
    val name: String,
    val rate: Int? = null
)

sealed interface CreateProjectProductionFailure {
    data class ValidationError(val errors: List<ValidationFailure>) : CreateProjectProductionFailure
    object DatabaseError : CreateProjectProductionFailure
}

suspend fun ApplicationCall.handleCreateProjectProduction() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val parameters = receiveParameters()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                projectResourceProductionItem(worldId, it)
            })
        },
        onFailure = { respond(HttpStatusCode.InternalServerError, "Failed to create project resource") }
    ) {
        step(Step.value(parameters))
            .step(ValidateCreateProjectProductionInputStep)
            .step(CreateProjectProductionStep(projectId))
    }
}

private object ValidateCreateProjectProductionInputStep : Step<Parameters, CreateProjectProductionFailure.ValidationError, CreateProjectProductionInput> {
    override suspend fun process(input: Parameters): Result<CreateProjectProductionFailure.ValidationError, CreateProjectProductionInput> {
        val name = ValidationSteps.required("name") { it }.process(input)
        val rate = ValidationSteps.optionalInt("ratePerHour") { it }.process(input)

        if (name is Result.Failure || rate is Result.Failure) {
            val errors = mutableListOf<ValidationFailure>()
            if (name is Result.Failure) errors.add(name.error)
            if (rate is Result.Failure) errors.add(rate.error)
            return Result.Failure(CreateProjectProductionFailure.ValidationError(errors))
        }

        return Result.Success(CreateProjectProductionInput(name.getOrNull()!!, rate.getOrNull() ?: 0))
    }
}

private data class CreateProjectProductionStep(val projectId: Int): Step<CreateProjectProductionInput, CreateProjectProductionFailure.DatabaseError, ProjectProduction> {
    override suspend fun process(input: CreateProjectProductionInput): Result<CreateProjectProductionFailure.DatabaseError, ProjectProduction> {
        return DatabaseSteps.update<CreateProjectProductionInput, CreateProjectProductionFailure.DatabaseError>(
            SafeSQL.insert("INSERT INTO project_productions (project_id, name, rate_per_hour) VALUES (?, ?, ?) RETURNING ID"),
            { statement, params ->
                statement.setInt(1, projectId)
                statement.setString(2, params.name)
                statement.setInt(3, params.rate ?: 0)
            },
            { CreateProjectProductionFailure.DatabaseError }
        ).process(input).map {
            ProjectProduction(
                id = it,
                projectId = projectId,
                name = input.name,
                ratePerHour = input.rate ?: 0
            )
        }
    }
}