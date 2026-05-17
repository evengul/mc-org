package app.mcorg.pipeline.resources

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleDeleteResourceGatheringItem() {
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    handlePipeline(
        onSuccess = { remainingCount ->
            if (remainingCount == 0) {
                // Row deleted (outerHTML swap to empty), plus OOB to show empty state
                val emptyState = createHTML().div("plan-empty-state") {
                    id = "plan-empty-state"
                    hxOutOfBands("outerHTML:#plan-empty-state")
                    p("plan-empty-state__text") { +"No resources defined yet." }
                    p("plan-empty-state__hint") { +"Add resources to start planning." }
                }
                respondHtml(emptyState)
            } else {
                respondHtml("")
            }
        }
    ) {
        DeleteResourceGatheringStep.run(resourceGatheringId)
        CacheManager.onResourceGatheringDeleted(projectId, resourceGatheringId)
        CountResourceGatheringItemsWithRequiredStep.run(projectId)
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

private object CountResourceGatheringItemsWithRequiredStep : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.query<Int, Int>(
            sql = SafeSQL.select("SELECT COUNT(*) FROM resource_gathering WHERE project_id = ? AND required > 0"),
            parameterSetter = { statement, projectId -> statement.setInt(1, projectId) },
            resultMapper = { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        ).process(input)
    }
}

