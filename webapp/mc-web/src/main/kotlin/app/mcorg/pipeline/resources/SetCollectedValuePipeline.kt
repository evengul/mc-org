package app.mcorg.pipeline.resources

import app.mcorg.event.ResourceCountUpdated
import app.mcorg.event.actorDisplayName
import app.mcorg.event.eventBus
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.resources.commonsteps.CountCollectedResourcesInProjectWithItemIdStep
import app.mcorg.pipeline.resources.commonsteps.CountTotalResourcesRequiredInProjectWithItemIdStep
import app.mcorg.pipeline.resources.commonsteps.GetResourceGatheringItemStep
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressByRgIdInput
import app.mcorg.pipeline.resources.commonsteps.UpsertProgressStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.dsl.progressBar
import app.mcorg.presentation.templated.dsl.resourceRow
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import java.time.Instant
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleSetCollectedValue() {
    val parameters = this.receiveParameters()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()
    val user = this.getUser()
    val bus = this.eventBus

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
        }
    ) {
        val value = ValidateSetCollectedValueStep.run(parameters)
        val before = GetUpdatedCollectedCountsStep.run(resourceGatheringId)
        UpsertProgressStep.run(UpsertProgressByRgIdInput(resourceGatheringId, value))
        val after = GetUpdatedCollectedCountsStep.run(resourceGatheringId)
        val project = GetProjectByIdStep.run(projectId)
        bus.publish(
            ResourceCountUpdated(
                worldId = worldId, actorId = user.id, timestamp = Instant.now(),
                projectId = projectId, itemId = after.item.itemId,
                previousDone = before.item.collected, newDone = after.item.collected,
                projectPreviousDone = before.totalCollected, projectNewDone = after.totalCollected,
                projectRequired = after.totalRequired,
                actorName = user.actorDisplayName(), projectName = project.name,
            )
        )
        after
    }
}

private object ValidateSetCollectedValueStep : Step<Parameters, AppFailure.ValidationError, Int> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Int> {
        return ValidationSteps.requiredInt("value") { AppFailure.ValidationError(listOf(it)) }
            .process(input)
    }
}

private object GetUpdatedCollectedCountsStep : Step<Int, AppFailure.DatabaseError, UpdatedProgress> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, UpdatedProgress> {
        val item = GetResourceGatheringItemStep.process(input)
        val required = CountTotalResourcesRequiredInProjectWithItemIdStep.process(input).getOrNull() ?: 0
        val collected = CountCollectedResourcesInProjectWithItemIdStep.process(input).getOrNull() ?: 0
        if (item is Result.Failure) return item
        return Result.success(UpdatedProgress(item.getOrNull()!!, required, collected))
    }
}
