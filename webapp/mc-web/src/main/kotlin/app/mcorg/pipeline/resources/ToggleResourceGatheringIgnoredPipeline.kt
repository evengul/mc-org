package app.mcorg.pipeline.resources

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.planResourcesAreaFragment
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

/**
 * MCO-247: toggles a resource_gathering row's `ignored` flag. Ignoring keeps the row
 * (reversible — the same endpoint un-ignores it) but excludes it from the derived
 * gathering plan (see [GenerateGatheringPlanStep]), so its share of any shared
 * intermediates recomputes without it.
 *
 * Responds with the whole `#plan-resources-area` fragment (active table + ignored
 * section) since a toggle moves the row between the two.
 */
suspend fun ApplicationCall.handleToggleResourceGatheringIgnored() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    handlePipeline(
        onSuccess = { resources ->
            respondHtml(planResourcesAreaFragment(worldId, projectId, resources))
        }
    ) {
        ToggleResourceGatheringIgnoredStep.run(resourceGatheringId)
        GetAllResourceGatheringItemsStep.run(projectId)
    }
}

private object ToggleResourceGatheringIgnoredStep : Step<Int, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.update("UPDATE resource_gathering SET ignored = NOT ignored WHERE id = ?"),
            parameterSetter = { statement, id -> statement.setInt(1, id) }
        ).process(input).map { }
    }
}
