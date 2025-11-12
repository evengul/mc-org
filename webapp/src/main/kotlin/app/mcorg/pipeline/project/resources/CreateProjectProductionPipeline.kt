package app.mcorg.pipeline.project.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.projectResourceProductionItem
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.div
import kotlinx.html.li
import kotlinx.html.stream.createHTML

data class CreateProjectProductionInput(
    val itemId: String,
    val name: String,
    val rate: Int? = null
)

suspend fun ApplicationCall.handleCreateProjectProduction() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val parameters = receiveParameters()

    val itemNames = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                projectResourceProductionItem(worldId, it)
            } + createHTML().div {
                hxOutOfBands("delete:#empty-resource-production-state")
            })
        }
    ) {
        value(parameters)
            .step(ValidateCreateProjectProductionInputStep(itemNames))
            .step(CreateProjectProductionStep(projectId))
    }
}

private data class ValidateCreateProjectProductionInputStep(val items: List<Item>) : Step<Parameters, AppFailure.ValidationError, CreateProjectProductionInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, CreateProjectProductionInput> {
        val itemId = ValidationSteps.required("itemId") { it }.process(input)
            .flatMap { existingItemId -> ValidationSteps.validateAllowedValues("itemId", items.map { it.id }, { it }, ignoreCase = false).process(existingItemId) }

        val rate = ValidationSteps.optionalInt("ratePerHour") { it }.process(input)

        if (itemId is Result.Failure || rate is Result.Failure) {
            val errors = mutableListOf<ValidationFailure>()
            if (itemId is Result.Failure) errors.add(itemId.error)
            if (rate is Result.Failure) errors.add(rate.error)
            return Result.Failure(AppFailure.ValidationError(errors))
        }

        val itemName = items.find { it.id == itemId.getOrNull()!! }!!.name

        return Result.Success(CreateProjectProductionInput(itemId.getOrNull()!!, itemName, rate.getOrNull() ?: 0))
    }
}

private data class CreateProjectProductionStep(val projectId: Int): Step<CreateProjectProductionInput, AppFailure.DatabaseError, ProjectProduction> {
    override suspend fun process(input: CreateProjectProductionInput): Result<AppFailure.DatabaseError, ProjectProduction> {
        return DatabaseSteps.update<CreateProjectProductionInput>(
            sql = SafeSQL.insert("INSERT INTO project_productions (project_id, name, rate_per_hour, item_id) VALUES (?, ?, ?, ?) RETURNING ID"),
            parameterSetter = { statement, params ->
                statement.setInt(1, projectId)
                statement.setString(2, params.name)
                statement.setInt(3, params.rate ?: 0)
                statement.setString(4, params.itemId)
            }
        ).process(input).map {
            ProjectProduction(
                id = it,
                projectId = projectId,
                name = input.name,
                itemId = input.itemId,
                ratePerHour = input.rate ?: 0
            )
        }
    }
}