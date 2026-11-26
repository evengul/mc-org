package app.mcorg.pipeline.resources

import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.resources.commonsteps.GetResourceGatheringItemStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.planResourceRow
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.stream.createHTML
import kotlinx.html.tr

/**
 * Adopt one measurement — the only way evidence becomes progress (MCO-539).
 *
 * The browser sends **no number**. It names a row, and the server reads what the chests said, so
 * "adopt" cannot be used as a general-purpose write to `collected` by anyone who can shape a POST.
 *
 * The swapped row carries the measurement it just adopted, which is what makes the chip disappear
 * on its own: the two numbers now agree, and the row draws no chip when the drift is zero.
 *
 * The row is the project page's "What I need" table row, the one surface the chip lives on since
 * the Projects tab went (MCO-692). It used to answer with that tab's row markup, which the project
 * page has no element for. No `#overall-progress` either: the page's total is the derived plan's,
 * and a sum over targets would put a different number in its place.
 */
suspend fun ApplicationCall.handleAdoptMeasurement() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    handlePipeline(
        onSuccess = { adopted ->
            respondHtml(createHTML().tr {
                planResourceRow(worldId, projectId, adopted.item, adopted.measured, adopted.followed)
            })
        }
    ) {
        val before = GetResourceGatheringItemStep.run(resourceGatheringId)
        AdoptMeasurementStep.run(AdoptMeasurementInput(projectId, before.itemId))
        AdoptedResourceStep(projectId).run(resourceGatheringId)
    }
}

/** The row as it stands after adoption, with the measurement that produced it. */
data class AdoptedResource(
    val item: ResourceGatheringItem,
    val measured: MeasuredStock?,
    /** Non-null on a storage-tracked project, whose redrawn row must stay read-only (MCO-540). */
    val followed: FollowedChests?,
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
        val measurements = (GetProjectMeasurementsStep.process(projectId).getOrNull() ?: ProjectMeasurements.NONE)
        return Result.success(
            AdoptedResource(
                item = item,
                measured = measurements[item.itemId],
                followed = measurements.followed,
            )
        )
    }
}
