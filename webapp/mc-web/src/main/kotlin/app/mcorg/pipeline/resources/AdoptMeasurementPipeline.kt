package app.mcorg.pipeline.resources

import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.resources.commonsteps.CountCollectedResourcesInProjectWithItemIdStep
import app.mcorg.pipeline.resources.commonsteps.CountTotalResourcesRequiredInProjectWithItemIdStep
import app.mcorg.pipeline.resources.commonsteps.GetResourceGatheringItemStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.presentation.templated.dsl.fieldLogSliceRowsFragment
import app.mcorg.presentation.templated.dsl.progressBar
import app.mcorg.presentation.templated.dsl.resourceRow
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.stream.createHTML

/**
 * Adopt one measurement — the only way evidence becomes progress (MCO-539).
 *
 * The browser sends **no number**. It names a row, and the server reads what the chests said, so
 * "adopt" cannot be used as a general-purpose write to `collected` by anyone who can shape a POST.
 *
 * The swapped row carries the measurement it just adopted, which is what makes the chip disappear
 * on its own: the two numbers now agree, and the row draws no chip when the drift is zero.
 */
suspend fun ApplicationCall.handleAdoptMeasurement() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    handlePipeline(
        onSuccess = { adopted ->
            respondHtml(
                createHTML().div {
                    resourceRow(
                        id = adopted.item.id,
                        worldId = worldId,
                        projectId = projectId,
                        itemName = adopted.item.name,
                        current = adopted.item.collected,
                        required = adopted.item.required,
                        source = adopted.item.solvedByProject?.second,
                        measured = adopted.measured,
                    )
                }.removePrefix("<div>").removeSuffix("</div>") +
                    createHTML().div {
                        id = "overall-progress"
                        hxOutOfBands("outerHTML:#overall-progress")
                        progressBar(adopted.totalCollected, adopted.totalRequired)
                    }
            )
        }
    ) {
        val before = GetResourceGatheringItemStep.run(resourceGatheringId)
        AdoptMeasurementStep.run(AdoptMeasurementInput(projectId, before.itemId))
        AdoptedResourceStep(projectId).run(resourceGatheringId)
    }
}

/**
 * Adopt every measurement on the project at once — "I have finished tagging, take the lot", which
 * is the realistic moment rather than clicking twelve chips in a row.
 *
 * Returns the whole row list rather than one row: it changed an unknown number of them, and the
 * client asked one question so it gets one answer.
 */
suspend fun ApplicationCall.handleAdoptAllMeasurements() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()

    handlePipeline(
        onSuccess = { rows -> respondHtml(fieldLogSliceRowsFragment(worldId, projectId, rows)) }
    ) {
        AdoptAllMeasurementsStep.run(projectId)
        GetAllResourceGatheringItemsStep.run(projectId)
    }
}

/** The row as it stands after adoption, with the measurement that produced it. */
data class AdoptedResource(
    val item: ResourceGatheringItem,
    val measured: MeasuredStock?,
    val totalRequired: Int,
    val totalCollected: Int,
)

/**
 * Re-reads the row *after* the write rather than predicting it.
 *
 * The adopt is clamped to `required` in SQL, so what the browser gets back is the value the
 * database actually holds — not the measurement it asked for, which may have been higher.
 */
private class AdoptedResourceStep(private val projectId: Int) :
    Step<Int, AppFailure.DatabaseError, AdoptedResource> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, AdoptedResource> {
        val item = when (val r = GetResourceGatheringItemStep.process(input)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        val measurements = GetProjectMeasurementsStep.process(projectId).getOrNull().orEmpty()
        return Result.success(
            AdoptedResource(
                item = item,
                measured = measurements[item.itemId],
                totalRequired = CountTotalResourcesRequiredInProjectWithItemIdStep.process(input).getOrNull() ?: 0,
                totalCollected = CountCollectedResourcesInProjectWithItemIdStep.process(input).getOrNull() ?: 0,
            )
        )
    }
}
