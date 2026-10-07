package app.mcorg.pipeline.resources

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

/**
 * Deletes a target and answers with the re-derived `#project-content`: the plan no longer asks for
 * it, so the breakdown changes along with the table, and with nothing left the plan's own empty
 * state is what renders (MCO-585). Both callers — the table's × and the panel's Remove — swap
 * `#project-content`, which also closes the panel on the resource that no longer exists.
 */
suspend fun ApplicationCall.handleDeleteResourceGatheringItem() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    handlePipeline(
        onSuccess = { respondHtml(listRerenderFragment(worldId, projectId) ?: return@handlePipeline) }
    ) {
        DeleteResourceGatheringStep.run(resourceGatheringId)
        CacheManager.onResourceGatheringDeleted(projectId, resourceGatheringId)
    }
}

private object DeleteResourceGatheringStep : Step<Int, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.delete("DELETE FROM resource_gathering WHERE id = ?"),
            parameterSetter = { statement, taskId ->
                statement.setInt(1, taskId)
            }
        ).process(input).map { }
    }
}
