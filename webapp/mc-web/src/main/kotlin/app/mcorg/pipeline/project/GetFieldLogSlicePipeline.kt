package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.ProjectListItem
import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.pipeline.project.commonsteps.GetProjectEdgesStep
import app.mcorg.pipeline.project.commonsteps.GetProjectListItemStep
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.fieldLogRowFragment
import app.mcorg.pipeline.resources.GetProjectMeasurementsStep
import app.mcorg.pipeline.resources.MeasuredStock
import app.mcorg.presentation.templated.dsl.fieldLogSliceRowsFragment
import app.mcorg.presentation.templated.dsl.sliceNextToGather
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

private data class FieldLogRowData(
    val project: ProjectListItem,
    val feeds: List<ProjectResourceEdge>,
    val blockedBy: List<ProjectResourceEdge>,
    val items: List<ResourceGatheringItem>,
    val measurements: Map<String, MeasuredStock>,
)

/**
 * Re-renders a single Field Log row, expanded (with its smart slice) or
 * collapsed — the HTMX target of clicking the row header.
 */
suspend fun ApplicationCall.handleGetFieldLogRow() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val expanded = request.queryParameters["expanded"] == "true"

    handlePipeline(
        onSuccess = { (project, feeds, blockedBy, items, measurements) ->
            respondHtml(
                fieldLogRowFragment(worldId, project, feeds, blockedBy, expanded, items, measurements),
            )
        }
    ) {
        val project = GetProjectListItemStep.run(projectId)
        val edges = GetProjectEdgesStep(worldId).run(Unit)
        val feeds = edges.filter { it.producerId == projectId && it.isLive }
            .distinctBy { Triple(it.producerId, it.consumerId, it.itemName) }
        val blockedBy = edges.filter { it.consumerId == projectId && it.isBlocking }
            .distinctBy { Triple(it.producerId, it.consumerId, it.itemName) }
        val items = if (expanded) GetAllResourceGatheringItemsStep.run(projectId) else emptyList()
        // Only when the slice is actually open: a collapsed row draws no resource rows, so the
        // query would be paid for on every project in the list and shown for none of them.
        val measurements =
            if (expanded) GetProjectMeasurementsStep.process(projectId).getOrNull().orEmpty() else emptyMap()
        FieldLogRowData(project, feeds, blockedBy, items, measurements)
    }
}

/** Filters the expanded slice's next-to-gather rows — the HTMX target of the slice filter input. */
suspend fun ApplicationCall.handleGetFieldLogSliceItems() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val query = request.queryParameters["query"]

    handlePipeline(
        onSuccess = { rows ->
            respondHtml(fieldLogSliceRowsFragment(worldId, projectId, rows.first, rows.second))
        }
    ) {
        val edges = GetProjectEdgesStep(worldId).run(Unit)
        val blockedProducerIds = edges
            .filter { it.consumerId == projectId && it.isBlocking }
            .map { it.producerId }
            .toSet()
        val items = GetAllResourceGatheringItemsStep.run(projectId)
        val measurements = GetProjectMeasurementsStep.process(projectId).getOrNull().orEmpty()
        sliceNextToGather(items, blockedProducerIds, query) to measurements
    }
}
