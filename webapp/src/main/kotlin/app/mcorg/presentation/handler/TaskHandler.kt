package app.mcorg.presentation.handler

import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.task.*
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.project.emptyTasksDisplay
import app.mcorg.presentation.templated.project.projectProgress
import app.mcorg.presentation.templated.project.taskCompletionCheckbox
import app.mcorg.presentation.templated.project.taskItem
import app.mcorg.presentation.templated.project.taskItemProgress
import app.mcorg.presentation.templated.project.tasksList
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.http.Parameters
import kotlinx.html.*
import kotlinx.html.stream.createHTML

object TaskHandler {

    suspend fun ApplicationCall.handleCreateTask() {
        val parameters = this.receiveParameters()
        val user = this.getUser()
        val worldId = this.getWorldId()
        val projectId = this.getProjectId()

        executePipeline(
            onSuccess = {
                respondHtml(createHTML().li {
                    taskItem(worldId, projectId, it.first)
                } + createHTML().div {
                    hxOutOfBands("delete:#empty-tasks-state")
                } + createHTML().div {
                    hxOutOfBands("innerHTML:#project-progress")
                    div {
                        projectProgress(it.second.second, it.second.first)
                    }
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
                .step(CreateTaskStep)
                .step(GetUpdatedTaskStep)
        }
    }

    suspend fun ApplicationCall.handleSearchTasks() {
        val user = this.getUser()
        val worldId = this.getWorldId()
        val projectId = this.getProjectId()

        val enrichedParameters = Parameters.build {
            appendAll(parameters)
            append("projectId", projectId.toString())
            append("userId", user.id.toString())
        }

        executePipeline(
            onSuccess = { result: SearchTasksResult ->
                val mainContent = createHTML().ul {
                    tasksList(worldId, projectId, result.tasks)
                }

                if (result.tasks.isEmpty()) {
                    respondHtml(mainContent + createHTML().p {
                        hxOutOfBands("innerHTML:#no-tasks-found")
                        + "No tasks found matching the search criteria."
                    })
                } else {
                    respondHtml(mainContent + createHTML().p {
                        hxOutOfBands("innerHTML:#no-tasks-found")
                        style = "display:none;"
                        + ""
                    })
                }
            },
            onFailure = { failure: SearchTasksFailures ->
                when (failure) {
                    is SearchTasksFailures.ValidationError ->
                        respondBadRequest("Invalid search parameters: ${failure.errors.joinToString(", ")}")
                    SearchTasksFailures.ProjectNotFound ->
                        respondBadRequest("Project not found")
                    SearchTasksFailures.InsufficientPermissions ->
                        respondBadRequest("You don't have permission to search tasks in this project")
                    SearchTasksFailures.DatabaseError ->
                        respondBadRequest("Failed to search tasks. Please try again.")
                }
            }
        ) {
            step(Step.value(enrichedParameters))
                .step(ValidateSearchTasksInputStep)
                .step(InjectSearchTasksContextStep)
                .step(SearchTasksStep)
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
                .step(ValidateTaskCompletionStep)
                .step(CompleteTaskStep)
                .step(CheckAnyTasksStepAfterCompletion)
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
                .step(ValidateTaskDependenciesStep)
                .step(DeleteTaskStep)
                .step(GetUpdatedTasksAfterDeletionStep)
        }
    }

    suspend fun ApplicationCall.handleUpdateRequirementProgress() {
        val parameters = this.receiveParameters()
        val user = this.getUser()
        val worldId = this.getWorldId()
        val projectId = this.getProjectId()
        val taskId = this.getTaskId()

        executePipeline(
            onSuccess = {
                var baseHtml = createHTML().div {
                    attributes["hx-swap-oob"] = "innerHTML:#project-progress"
                    div {
                        projectProgress(it.second.second, it.second.first)
                    }
                }
                if (it.first.isCompleted()) {
                    baseHtml += createHTML().input {
                        hxOutOfBands("true")
                        taskCompletionCheckbox(
                            worldId = worldId,
                            projectId = projectId,
                            taskId = taskId,
                            completed = true
                        )
                    }
                }
                if (it.first.requirement is ItemRequirement) {
                    respondHtml(baseHtml + createHTML().li {
                        taskItemProgress(taskId, it.first.requirement as ItemRequirement)
                    })
                } else {
                    respondHtml(baseHtml)
                }
            },
            onFailure = { failure: UpdateRequirementFailures ->
                when (failure) {
                    is UpdateRequirementFailures.ValidationError ->
                        respondBadRequest("Invalid input: ${failure.errors.joinToString(", ")}")
                    UpdateRequirementFailures.RequirementNotFound ->
                        respondBadRequest("Requirement not found")
                    UpdateRequirementFailures.InsufficientPermissions ->
                        respondBadRequest("You don't have permission to update this requirement")
                    UpdateRequirementFailures.InvalidAmount ->
                        respondBadRequest("Invalid amount specified")
                    UpdateRequirementFailures.DatabaseError ->
                        respondBadRequest("Failed to update requirement. Please try again.")
                }
            }
        ) {
            step(Step.value(parameters))
                .step(ValidateRequirementProgressInputStep)
                .step(InjectRequirementContextStep(taskId, projectId, user.id))
                .step(UpdateItemRequirementProgressStep)
                .step(Step.value(taskId))
                .step(object : Step<Int, UpdateRequirementFailures, Pair<Task, Pair<Int, Int>>> {
                    override suspend fun process(input: Int): Result<UpdateRequirementFailures, Pair<Task, Pair<Int, Int>>> {
                        val task = GetTaskStep.process(input)

                        val taskCount = CountTasksInProjectWithTaskIdStep.process(input).getOrNull() ?: 0
                        val completedCount = CountCompletedTasksStep.process(input).getOrNull() ?: 0

                        if (task is Result.Failure) {
                            return Result.failure(UpdateRequirementFailures.DatabaseError)
                        }

                        return Result.success(Pair(task.getOrNull()!!, Pair(taskCount, completedCount)))
                    }
                })
        }
    }

    suspend fun ApplicationCall.handleToggleActionRequirement() {
        val user = this.getUser()
        val projectId = this.getProjectId()
        val taskId = this.getTaskId()

        // Create empty parameters for action toggle (no amount needed)
        val emptyParameters = Parameters.Empty

        executePipeline(
            onSuccess = {
                respondHtml(createHTML().div {
                    attributes["hx-swap-oob"] = "innerHTML:#project-progress"
                    div {
                        projectProgress(it.second, it.first)
                    }
                })
            },
            onFailure = { failure: UpdateRequirementFailures ->
                when (failure) {
                    UpdateRequirementFailures.RequirementNotFound ->
                        respondBadRequest("Requirement not found")
                    UpdateRequirementFailures.InsufficientPermissions ->
                        respondBadRequest("You don't have permission to update this requirement")
                    UpdateRequirementFailures.DatabaseError ->
                        respondBadRequest("Failed to update requirement. Please try again.")
                    else ->
                        respondBadRequest("Failed to update requirement")
                }
            }
        ) {
            step(Step.value(emptyParameters))
                .step(ValidateRequirementProgressInputStep)
                .step(InjectRequirementContextStep(taskId, projectId, user.id))
                .step(UpdateItemRequirementProgressStep)
                .step(Step.value(projectId))
                .step(CheckTaskCompletionStep)
        }
    }
}
