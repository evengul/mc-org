package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.task.commonsteps.CountCompletedTasksStep
import app.mcorg.pipeline.task.commonsteps.CountTasksInProjectWithTaskIdStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.project.projectProgress
import app.mcorg.presentation.utils.getTaskId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleToggleActionRequirement() {
    val taskId = this.getTaskId()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().div {
                attributes["hx-swap-oob"] = "innerHTML:#project-progress"
                div {
                    projectProgress(it.second, it.first)
                }
            })
        },
        onFailure = { respond(HttpStatusCode.InternalServerError) }
    ) {
        value(taskId)
            .step(ToggleActionTaskStep)
            .step(CheckTaskCompletionStep)
    }
}

private object ToggleActionTaskStep : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.update("""
                UPDATE tasks
                SET requirement_action_completed = NOT requirement_action_completed
                WHERE id = ?
            """.trimIndent()),
            parameterSetter = { stmt, taskId ->
                stmt.setInt(1, taskId)
            }
        ).process(input).map { input }
    }
}

object CheckTaskCompletionStep : Step<Int, Nothing, Pair<Int, Int>> {
    override suspend fun process(input: Int): Result<Nothing, Pair<Int, Int>> {
        val totalTasks = CountTasksInProjectWithTaskIdStep.process(input).getOrNull() ?: 0
        val completedTasks = CountCompletedTasksStep.process(input).getOrNull() ?: 0

        return Result.success(Pair(totalTasks, completedTasks))
    }
}