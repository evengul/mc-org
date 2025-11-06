package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.task.commonsteps.CountCompletedTasksStep
import app.mcorg.pipeline.task.commonsteps.CountTasksInProjectWithTaskIdStep
import app.mcorg.pipeline.task.commonsteps.GetTaskStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.projectProgress
import app.mcorg.presentation.templated.project.taskCompletionCheckbox
import app.mcorg.presentation.templated.project.taskItemProgress
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getTaskId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

suspend fun ApplicationCall.handleCompleteTask() {
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val taskId = this.getTaskId()

    executePipeline(
        onSuccess = {
            val baseHtml = createHTML().input {
                taskCompletionCheckbox(
                    worldId,
                    projectId,
                    taskId,
                    it.first.isCompleted()
                )
            } + createHTML().div {
                attributes["hx-swap-oob"] = "innerHTML:#project-progress"
                div {
                    projectProgress(it.second.second, it.second.first)
                }
            }
            if (it.first.requirement is ItemRequirement) {
                respondHtml(baseHtml + createHTML().ul {
                    hxOutOfBands("innerHTML:#task-item-${taskId}-progress")
                    li {
                        taskItemProgress(taskId, it.first.requirement as ItemRequirement)
                    }
                })
            } else {
                respondHtml(baseHtml)
            }
        },
        onFailure = { respond(HttpStatusCode.InternalServerError) }
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
            sql = SafeSQL.select("SELECT 1 FROM tasks WHERE (requirement_action_completed = FALSE OR requirement_item_collected < requirement_item_required_amount) AND id = ?"),
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
                UPDATE tasks
                SET updated_at = CURRENT_TIMESTAMP,
                requirement_action_completed = CASE WHEN requirement_type = 'ACTION' THEN TRUE ELSE requirement_action_completed END,
                requirement_item_collected = CASE WHEN requirement_type = 'ITEM' THEN requirement_item_required_amount ELSE requirement_item_collected END
                WHERE id = ? AND (requirement_action_completed = FALSE OR requirement_item_collected < requirement_item_required_amount)
            """.trimIndent()),
            parameterSetter = { statement, taskId -> statement.setInt(1, taskId) }
        ).process(input).map { input }
    }
}

private object CheckAnyTasksStepAfterCompletion : Step<Int, AppFailure.DatabaseError, Pair<Task, Pair<Int, Int>>> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Pair<Task, Pair<Int, Int>>> {
        val task = GetTaskStep.process(input)

        val tasksCount = CountTasksInProjectWithTaskIdStep.process(input).getOrNull() ?: 0
        val completedCount = CountCompletedTasksStep.process(input).getOrNull() ?: 0

        if (task is Result.Failure) {
            return task
        }

        return Result.success(Pair(task.getOrNull()!!, Pair(tasksCount, completedCount)))
    }
}
