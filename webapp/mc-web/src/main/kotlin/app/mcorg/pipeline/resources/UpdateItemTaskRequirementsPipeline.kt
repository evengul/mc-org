package app.mcorg.pipeline.resources

import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.resources.commonsteps.CountCollectedResourcesInProjectWithItemIdStep
import app.mcorg.pipeline.resources.commonsteps.CountTotalResourcesRequiredInProjectWithItemIdStep
import app.mcorg.pipeline.resources.commonsteps.GetResourceGatheringItemStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.dsl.progressBar
import app.mcorg.presentation.templated.dsl.resourceRow
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.stream.createHTML

data class UpdatedProgress(
    val item: ResourceGatheringItem,
    val totalRequired: Int,
    val totalCollected: Int
)

suspend fun ApplicationCall.handleUpdateRequirementProgress() {
    val parameters = this.receiveParameters()
    val resourceGatheringId = this.getResourceGatheringId()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()

    handlePipeline(
        onSuccess = { progress ->
            respondHtml(
                createHTML().div {
                    resourceRow(
                        id = progress.item.id,
                        worldId = worldId,
                        projectId = projectId,
                        itemName = progress.item.name,
                        current = progress.item.collected,
                        required = progress.item.required,
                        source = progress.item.solvedByProject?.second
                    )
                }.removePrefix("<div>").removeSuffix("</div>") +
                createHTML().div {
                    id = "overall-progress"
                    hxOutOfBands("outerHTML:#overall-progress")
                    progressBar(progress.totalCollected, progress.totalRequired)
                }
            )
        },
    ) {
        val amount = ValidateUpdateItemTaskRequirementsInputStep.run(parameters)
        UpdateItemTaskRequirement(resourceGatheringId).run(amount)
        GetUpdatedTaskCountsStep.run(resourceGatheringId)
    }
}

private object ValidateUpdateItemTaskRequirementsInputStep : Step<Parameters, AppFailure.ValidationError, Int> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Int> {
        val amount = ValidationSteps.requiredInt("amount") { it }
            .process(input)
            .flatMap { providedAmount ->
                if (providedAmount == 0) {
                    Result.failure(ValidationFailure.InvalidValue("amount", listOf("any non-zero integer")))
                } else {
                    Result.success(providedAmount)
                }
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
            sql = SafeSQL.update("UPDATE resource_gathering SET collected = GREATEST(LEAST(collected + ?, required), 0) WHERE id = ?"),
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
