package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.task.*
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.project.tasksList
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.stream.createHTML

object TaskHandler {

    suspend fun ApplicationCall.handleCreateTask() {
        val parameters = this.receiveParameters()
        val user = this.getUser()
        val worldId = this.getWorldId()
        val projectId = this.getProjectId()

        executePipeline(
            onSuccess = { result: CreateTaskResult ->
                respondHtml(createHTML().div {
                    tasksList(worldId, projectId, result.updatedTasks)
                })
            },
            onFailure = { failure: CreateTaskFailures ->
                when (failure) {
                    is CreateTaskFailures.ValidationError ->
                        respondBadRequest("Validation failed: ${failure.errors.joinToString(", ")}")
                    CreateTaskFailures.InsufficientPermissions ->
                        respondBadRequest("You don't have permission to create tasks in this project")
                    CreateTaskFailures.ProjectNotFound ->
                        respondBadRequest("Project not found")
                    CreateTaskFailures.DatabaseError ->
                        respondBadRequest("Failed to create task. Please try again.")
                }
            }
        ) {
            step(Step.value(parameters))
                .step(ValidateTaskInputStep)
                .step(InjectTaskContextStep(projectId, user.id))
                .step(ValidateProjectAccessStep)
                .step(CreateTaskStep)
                .step(GetUpdatedTasksStep)
        }
    }

    suspend fun ApplicationCall.handleCompleteTask() {
        val user = this.getUser()
        val worldId = this.getWorldId()
        val projectId = this.getProjectId()
        val taskId = this.getTaskId()

        val input = CompleteTaskInput(
            taskId = taskId,
            userId = user.id,
            projectId = projectId
        )

        executePipeline(
            onSuccess = { result: CompleteTaskResult ->
                val task = result.task
                respondHtml(createHTML().input {
                    id = "task-${task.id}-complete"
                    checked = true
                    disabled = true
                    hxSwap("outerHTML")
                    hxTarget("#task-${task.id}-complete")
                    hxPatch(Link.Worlds.world(worldId).project(projectId).tasks().task(task.id) + "/complete")
                    type = InputType.checkBox
                })
            },
            onFailure = { failure: CompleteTaskFailures ->
                when (failure) {
                    CompleteTaskFailures.TaskNotFound ->
                        respondBadRequest("Task not found")
                    CompleteTaskFailures.TaskAlreadyCompleted ->
                        respondBadRequest("Task is already completed")
                    CompleteTaskFailures.InsufficientPermissions ->
                        respondBadRequest("You don't have permission to complete this task")
                    CompleteTaskFailures.DatabaseError ->
                        respondBadRequest("Failed to complete task. Please try again.")
                }
            }
        ) {
            step(Step.value(input))
                .step(ValidateTaskAccessStep)
                .step(ValidateTaskCompletionStep)
                .step(CompleteTaskStep)
                .step(CheckProjectProgressStep)
                .step(object : Step<Boolean, CompleteTaskFailures, CompleteTaskResult> {
                    override suspend fun process(progressResult: Boolean): app.mcorg.domain.pipeline.Result<CompleteTaskFailures, CompleteTaskResult> {
                        return GetUpdatedTasksAfterCompletionStep.process(Pair(input, progressResult))
                    }
                })
        }
    }

    suspend fun ApplicationCall.handleDeleteTask() {
        val user = this.getUser()
        val projectId = this.getProjectId()
        val taskId = this.getTaskId()

        val input = DeleteTaskInput(
            taskId = taskId,
            userId = user.id,
            projectId = projectId
        )

        executePipeline(
            onSuccess = { result: DeleteTaskResult ->
                respondHtml(createHTML().div {
                    div("notice notice--success") {
                        +"Task deleted successfully"
                    }
                    // Return updated tasks list fragment
                    id = "tasks-list"
                })
            },
            onFailure = { failure: DeleteTaskFailures ->
                when (failure) {
                    DeleteTaskFailures.TaskNotFound ->
                        respondBadRequest("Task not found")
                    DeleteTaskFailures.InsufficientPermissions ->
                        respondBadRequest("You don't have permission to delete this task")
                    DeleteTaskFailures.TaskHasDependencies ->
                        respondBadRequest("Cannot delete task that other tasks depend on")
                    DeleteTaskFailures.DatabaseError ->
                        respondBadRequest("Failed to delete task. Please try again.")
                }
            }
        ) {
            step(Step.value(input))
                .step(ValidateTaskOwnershipStep)
                .step(ValidateTaskDependenciesStep)
                .step(DeleteTaskStep)
                .step(GetUpdatedTasksAfterDeletionStep)
        }
    }
}
