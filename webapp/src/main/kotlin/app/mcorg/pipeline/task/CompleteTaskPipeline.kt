package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.ActionTask
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.task.commonsteps.CountActionTasksInProjectWithTaskIdStep
import app.mcorg.pipeline.task.commonsteps.CountCompletedActionTasksStep
import app.mcorg.pipeline.task.commonsteps.GetActionTaskStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.project.projectProgress
import app.mcorg.presentation.templated.project.taskCompletionCheckbox
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getTaskId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleCompleteActionTask() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val taskId = this.getTaskId()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().input {
                taskCompletionCheckbox(
                    worldId,
                    projectId,
                    taskId,
                    it.first.completed
                )
            } + createHTML().div {
                attributes["hx-swap-oob"] = "innerHTML:#project-progress"
                div {
                    projectProgress(it.second.second, it.second.first)
                }
            })
        },
    ) {
        value(taskId)
            .step(ValidateTaskCompletionStep)
            .step(CompleteTaskStep)
            .step(CheckAnyTasksStepAfterCompletion)
    }
}

private object ValidateTaskCompletionStep : Step<Int, AppFailure, Int> {
    override suspend fun process(input: Int): Result<AppFailure, Int> {
        val result = DatabaseSteps.query<Int, Boolean>(
            sql = SafeSQL.select("SELECT 1 FROM action_task WHERE (completed = FALSE) AND id = ?"),
            parameterSetter = { statement, taskId ->
                statement.setInt(1, taskId)
            },
            resultMapper = { it.next() }
        ).process(input)

        return when(result) {
            is Result.Failure -> result
            is Result.Success -> when (result.value) {
                false -> Result.failure(AppFailure.customValidationError("task", "Task is already completed"))
                true -> Result.success(input)
            }
        }
    }
}

private object CompleteTaskStep : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.update("""
                UPDATE action_task
                SET updated_at = CURRENT_TIMESTAMP,
                completed = TRUE
                WHERE id = ?
            """.trimIndent()),
            parameterSetter = { statement, taskId -> statement.setInt(1, taskId) }
        ).process(input).map { input }
    }
}

private object CheckAnyTasksStepAfterCompletion : Step<Int, AppFailure.DatabaseError, Pair<ActionTask, Pair<Int, Int>>> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Pair<ActionTask, Pair<Int, Int>>> {
        val task = GetActionTaskStep.process(input)

        val tasksCount = CountActionTasksInProjectWithTaskIdStep.process(input).getOrNull() ?: 0
        val completedCount = CountCompletedActionTasksStep.process(input).getOrNull() ?: 0

        if (task is Result.Failure) {
            return task
        }

        return Result.success(Pair(task.getOrNull()!!, Pair(tasksCount, completedCount)))
    }
}
