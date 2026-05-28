package app.mcorg.pipeline.resources

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.resources.GetItemsInWorldVersionStep
import app.mcorg.pipeline.resources.commonsteps.GetResourceGatheringItemStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.dsl.pages.planResourceRow
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.html.tr

private data class CreateResourceGatheringItemInput(
    val name: String,
    val itemId: String,
    val requiredAmount: Int
)

suspend fun ApplicationCall.handleCreateResourceGatheringItem() {
    val parameters = this.receiveParameters()

    val worldId = this.getWorldId()
    val projectId = this.getProjectId()

    val itemNames = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    handlePipeline(
        onSuccess = { item ->
            respondHtml(createHTML().tr {
                planResourceRow(worldId, projectId, item)
            } + createHTML().div {
                hxOutOfBands("delete:#plan-empty-state")
            })
        }
    ) {
        val input = ValidateCreateResourceGatheringItemInputStep(itemNames).run(parameters)
        val id = CreateResourceGatheringItemStep(projectId).run(input)
        CacheManager.onResourceGatheringCreated(projectId, id)
        GetResourceGatheringItemStep.run(id)
    }
}

private data class ValidateCreateResourceGatheringItemInputStep(val validItems: List<Item>) :
    Step<Parameters, AppFailure.ValidationError, CreateResourceGatheringItemInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, CreateResourceGatheringItemInput> {
        val itemId = ValidationSteps.required("requiredItemId") { it }.process(input)
            .flatMap { itemId ->
                ValidationSteps.validateAllowedValues(
                    "requiredItemId",
                    validItems.map { item -> item.id },
                    { it },
                    ignoreCase = false
                ).process(itemId)
            }

        val requiredAmount = ValidationSteps.requiredInt("requiredAmount") { it }.process(input)
            .flatMap { requiredAmount ->
                ValidationSteps.validateRange("requiredAmount", 1, Int.MAX_VALUE) { it }.process(requiredAmount)
            }

        val errors = mutableListOf<ValidationFailure>()
        if (itemId is Result.Failure) {
            errors.add(itemId.error)
        }
        if (requiredAmount is Result.Failure) {
            errors.add(requiredAmount.error)
        }

        return if (errors.isEmpty()) {
            val name = validItems.find { item -> item.id == itemId.getOrThrow() }?.name ?: throw IllegalStateException("Item was found earlier, must be present")
            Result.success(
                CreateResourceGatheringItemInput(
                    name = name,
                    itemId = (itemId as Result.Success).value,
                    requiredAmount = (requiredAmount as Result.Success).value
                )
            )
        } else {
            Result.failure(AppFailure.ValidationError(errors))
        }
    }
}

private data class CreateResourceGatheringItemStep(val projectId: Int) : Step<CreateResourceGatheringItemInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: CreateResourceGatheringItemInput): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<CreateResourceGatheringItemInput>(
            sql = SafeSQL.insert("""
                INSERT INTO resource_gathering (project_id, name, item_id, required)
                VALUES (?, ?, ?, ?)
                RETURNING id
            """.trimIndent()),
            parameterSetter = { statement, createInput ->
                statement.setInt(1, projectId)
                statement.setString(2, createInput.name)
                statement.setString(3, createInput.itemId)
                statement.setInt(4, createInput.requiredAmount)
            }
        ).process(input)
    }
}

