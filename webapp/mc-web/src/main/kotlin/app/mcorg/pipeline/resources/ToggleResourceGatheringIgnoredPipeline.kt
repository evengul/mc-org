package app.mcorg.pipeline.resources

import app.mcorg.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.handlePipeline
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
 * Responds with the whole re-derived `#project-content`, not just the resource table: a toggle
 * moves the row between the table and the ignored section, and it changes the plan the table is
 * grouped by and the breakdown is drawn from. A table rendered without that plan lost every group
 * heading until a reload.
 */
suspend fun ApplicationCall.handleToggleResourceGatheringIgnored() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    handlePipeline(
        onSuccess = { respondHtml(listRerenderFragment(worldId, projectId) ?: return@handlePipeline) }
    ) {
        ToggleResourceGatheringIgnoredStep.run(resourceGatheringId)
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
