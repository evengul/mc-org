package app.mcorg.pipeline.resources

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.resourceGatheringProgress
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getResourceGatheringId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleDeleteResourceGatheringItem() {
    val projectId = this.getProjectId()
    val resourceGatheringId = this.getResourceGatheringId()

    executePipeline(
        onSuccess = {
            val progressContent = createHTML().div {
                hxOutOfBands("innerHTML:#resource-gathering-total-progress")
                div {
                    resourceGatheringProgress(
                        "resource-gathering-total-progress",
                        it.second,
                        it.first
                    )
                }
            }
            respondHtml(progressContent)
        },
    ) {
        value(resourceGatheringId)
            .step(DeleteResourceGatheringStep)
            .value(projectId)
            .step(GetUpdatedResourceGatheringStep)
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

private object GetUpdatedResourceGatheringStep : Step<Int, Nothing, Pair<Int, Int>> {
    override suspend fun process(input: Int): Result<Nothing, Pair<Int, Int>> {
        val taskCount = CountResourceGatheringItemsInProjectStep.process(input).getOrNull() ?: 0
        val completedCount = CountCompletedItemsInProjectStep.process(input).getOrNull() ?: 0

        return Result.success(Pair(taskCount, completedCount))
    }
}

private object CountResourceGatheringItemsInProjectStep : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.query<Int, Int>(
            sql = SafeSQL.select("""
                SELECT SUM(required) FROM resource_gathering WHERE project_id = ?
            """.trimIndent()),
            parameterSetter = { statement, taskId -> statement.setInt(1, taskId) },
            resultMapper = { rs ->
                if (rs.next()) {
                    rs.getInt(1)
                } else {
                    0
                }
            }
        ).process(input)
    }
}

private object CountCompletedItemsInProjectStep : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.query<Int, Int>(
            sql = SafeSQL.select("""
                SELECT SUM(collected) FROM resource_gathering WHERE project_id = ?
            """.trimIndent()),
            parameterSetter = { statement, taskId -> statement.setInt(1, taskId) },
            resultMapper = { rs ->
                if (rs.next()) {
                    rs.getInt(1)
                } else {
                    0
                }
            }
        ).process(input)
    }
}
