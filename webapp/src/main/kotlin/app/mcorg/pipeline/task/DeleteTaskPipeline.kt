package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.task.commonsteps.CountProjectTasksStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.project.emptyTasksDisplay
import app.mcorg.presentation.templated.project.projectProgress
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getTaskId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleDeleteTask() {
    val projectId = this.getProjectId()
    val taskId = this.getTaskId()

    executePipeline(
        onSuccess = {
            val baseHtml = createHTML().div {
                attributes["hx-swap-oob"] = "innerHTML:#project-progress"
                div {
                    projectProgress(it.second, it.first)
                }
            }
            if (it.first == 0) {
                respondHtml(baseHtml + createHTML().div {
                    attributes["hx-swap-oob"] = "beforebegin:#tasks-list"
                    div {
                        emptyTasksDisplay()
                    }
                })
                return@executePipeline
            } else {
                respondHtml(baseHtml)
            }
        },
    ) {
        value(taskId)
            .step(DeleteTaskStep)
            .value(projectId)
            .step(GetUpdatedTasksAfterDeletionStep)
    }
}

private object DeleteTaskStep : Step<Int, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.delete("DELETE FROM tasks WHERE id = ?"),
            parameterSetter = { statement, taskId ->
                statement.setInt(1, taskId)
            }
        ).process(input).map { }
    }
}

private object GetUpdatedTasksAfterDeletionStep : Step<Int, Nothing, Pair<Int, Int>> {
    override suspend fun process(input: Int): Result<Nothing, Pair<Int, Int>> {
        val taskCount = CountProjectTasksStep.process(input).getOrNull() ?: 0
        val completedCount = CountCompletedTasksInProjectStep.process(input).getOrNull() ?: 0

        return Result.success(Pair(taskCount, completedCount))
    }
}

private object CountCompletedTasksInProjectStep : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.query<Int, Int>(
            sql = SafeSQL.select("""
                SELECT COUNT(id) FROM tasks WHERE project_id = ? AND (
                    (requirement_type = 'ITEM' AND requirement_item_collected >= requirement_item_required_amount)
                    OR
                    (requirement_type = 'ACTION' AND requirement_action_completed = TRUE)
                )
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
