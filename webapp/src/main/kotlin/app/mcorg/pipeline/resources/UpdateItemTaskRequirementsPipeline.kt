package app.mcorg.pipeline.resources

import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.resources.commonsteps.CountCollectedResourcesInProjectWithItemIdStep
import app.mcorg.pipeline.resources.commonsteps.CountTotalResourcesRequiredInProjectWithItemIdStep
import app.mcorg.pipeline.resources.commonsteps.GetResourceGatheringItemStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.resourceGatheringProgress
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.stream.createHTML

data class UpdatedProgress(
    val item: ResourceGatheringItem,
    val totalRequired: Int,
    val totalCollected: Int
)

suspend fun ApplicationCall.handleUpdateRequirementProgress() {
    val parameters = this.receiveParameters()
    val taskId = this.getResourceGatheringId()

    executePipeline(
        onSuccess = {
            respondHtml(
                createHTML().div {
                    resourceGatheringProgress(
                        divId = "resource-gathering-item-${it.item.id}-progress",
                        collected = it.item.collected,
                        required = it.item.required
                    )
                }.removePrefix("<div>").removeSuffix("</div>") + createHTML().div {
                    hxOutOfBands("innerHTML:#resource-gathering-total-progress")
                    div {
                        resourceGatheringProgress(
                            "resource-gathering-total-progress",
                            it.totalCollected,
                            it.totalRequired
                        )
                    }
                }
            )
        },
    ) {
        step(Step.value(parameters))
            .step(ValidateUpdateItemTaskRequirementsInputStep)
            .step(UpdateItemTaskRequirement(taskId))
            .value(taskId)
            .step(GetUpdatedTaskCountsStep)
    }
}

private object ValidateUpdateItemTaskRequirementsInputStep : Step<Parameters, AppFailure.ValidationError, Int> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Int> {
        val amount = ValidationSteps.requiredInt("amount") { it }
            .process(input)
            .flatMap { providedAmount ->
                ValidationSteps.validateRange("amount", 1, Int.MAX_VALUE) { it }
                    .process(providedAmount)
            }

        return when (amount) {
            is Result.Success -> Result.success(amount.value)
            is Result.Failure -> Result.failure(AppFailure.ValidationError(listOf(amount.error)))
        }
    }
}

private data class UpdateItemTaskRequirement(val taskId: Int) : Step<Int, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.update("UPDATE resource_gathering SET collected = LEAST(collected + ?, required) WHERE id = ?"),
            parameterSetter = { statement, amount ->
                statement.setInt(1, amount)
                statement.setInt(2, taskId)
            }
        ).process(input).map {  }
    }
}

private object GetUpdatedTaskCountsStep : Step<Int, AppFailure.DatabaseError, UpdatedProgress> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, UpdatedProgress> {
        val task = GetResourceGatheringItemStep.process(input)

        val required = CountTotalResourcesRequiredInProjectWithItemIdStep.process(input).getOrNull() ?: 0
        val collected = CountCollectedResourcesInProjectWithItemIdStep.process(input).getOrNull() ?: 0

        if (task is Result.Failure) {
            return task
        }

        return Result.success(
            UpdatedProgress(
                item = task.getOrNull()!!,
                totalRequired = required,
                totalCollected = collected
            )
        )
    }
}