package app.mcorg.pipeline.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.resources.GetItemsInWorldVersionStep
import app.mcorg.pipeline.resources.commonsteps.CountCollectedResourcesInProjectWithItemIdStep
import app.mcorg.pipeline.resources.commonsteps.CountTotalResourcesRequiredInProjectWithItemIdStep
import app.mcorg.pipeline.resources.commonsteps.GetResourceGatheringItemStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.projectResourceGatheringItem
import app.mcorg.presentation.templated.project.resourceGatheringProgress
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.li
import kotlinx.html.stream.createHTML

private data class CreateResourceGatheringItemInput(
    val name: String,
    val itemId: String,
    val requiredAmount: Int
)

private data class UpdatedResourceGatheringProgress(
    val gatheringItem: ResourceGatheringItem,
    val totalResourceRequired: Int,
    val totalResourcesCollected: Int
)

suspend fun ApplicationCall.handleCreateResourceGatheringItem() {
    val parameters = this.receiveParameters()

    val worldId = this.getWorldId()
    val projectId = this.getProjectId()

    val itemNames = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                projectResourceGatheringItem(worldId, projectId, it.gatheringItem)
            } + createHTML().div {
                hxOutOfBands("delete:#empty-resource-gathering-state")
            } + createHTML().div {
                hxOutOfBands("innerHTML:#resource-gathering-total-progress")
                div {
                    resourceGatheringProgress(
                        "resource-gathering-total-progress",
                        it.totalResourcesCollected,
                        it.totalResourceRequired
                    )
                }
            })
        }
    ) {
        value(parameters)
            .step(ValidateCreateResourceGatheringItemInputStep(itemNames))
            .step(CreateResourceGatheringItemStep(projectId))
            .step(GetUpdatedResourceGatheringProgressStep)
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

private object GetUpdatedResourceGatheringProgressStep : Step<Int, AppFailure.DatabaseError, UpdatedResourceGatheringProgress> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, UpdatedResourceGatheringProgress> {
        val item = GetResourceGatheringItemStep.process(input)

        if (item is Result.Failure) {
            return item
        }

        val required = CountTotalResourcesRequiredInProjectWithItemIdStep.process(input).getOrNull() ?: 0
        val collected = CountCollectedResourcesInProjectWithItemIdStep.process(input).getOrNull() ?: 0

        return Result.success(
            UpdatedResourceGatheringProgress(
                gatheringItem = item.getOrNull()!!,
                totalResourceRequired = required,
                totalResourcesCollected = collected
            )
        )
    }
}