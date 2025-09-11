package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.task.*
import app.mcorg.domain.model.task.ActionRequirement
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.project.requirement
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
            onSuccess = { result: CreateTaskResult ->
                respondHtml(createHTML().ul {
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
                respondHtml(createHTML().ul {
                    tasksList(worldId, projectId, result.tasks)
                })
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
                .step(ValidateSearchTasksAccessStep)
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

    suspend fun ApplicationCall.handleUpdateRequirementProgress() {
        val parameters = this.receiveParameters()
        val user = this.getUser()
        val worldId = this.getWorldId()
        val projectId = this.getProjectId()
        val taskId = this.getTaskId()
        val requirementId = this.parameters["requirementId"]?.toIntOrNull()
            ?: return respondBadRequest("Invalid requirement ID")

        executePipeline(
            onSuccess = { result: UpdateRequirementProgressResult ->
                respondHtml(createHTML().li {
                    requirement(result.requirement, worldId, projectId, taskId)
                })
            },
            onFailure = { failure: UpdateRequirementFailures ->
                when (failure) {
                    is UpdateRequirementFailures.ValidationError ->
                        respondBadRequest("Invalid input: ${failure.errors.joinToString(", ")}")
                    UpdateRequirementFailures.RequirementNotFound ->
                        respondBadRequest("Requirement not found")
                    UpdateRequirementFailures.RequirementAlreadyCompleted ->
                        respondBadRequest("Requirement is already completed")
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
                .step(InjectRequirementContextStep(requirementId, taskId, projectId, user.id))
                .step(ValidateRequirementAccessStep)
                .step(GetRequirementStep)
                .step(UpdateItemRequirementProgressStep)
                .step(CheckTaskCompletionStep)
        }
    }

    suspend fun ApplicationCall.handleToggleActionRequirement() {
        val user = this.getUser()
        val worldId = this.getWorldId()
        val projectId = this.getProjectId()
        val taskId = this.getTaskId()
        val requirementId = this.parameters["requirementId"]?.toIntOrNull()
            ?: return respondBadRequest("Invalid requirement ID")

        // Create empty parameters for action toggle (no amount needed)
        val emptyParameters = Parameters.Empty

        executePipeline(
            onSuccess = { result: UpdateRequirementProgressResult ->
                respondHtml(createHTML().li {
                    requirement(result.requirement, worldId, projectId, taskId)
                })
            },
            onFailure = { failure: UpdateRequirementFailures ->
                when (failure) {
                    UpdateRequirementFailures.RequirementNotFound ->
                        respondBadRequest("Requirement not found")
                    UpdateRequirementFailures.RequirementAlreadyCompleted ->
                        respondBadRequest("Requirement is already completed")
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
                .step(InjectRequirementContextStep(requirementId, taskId, projectId, user.id))
                .step(ValidateRequirementAccessStep)
                .step(GetRequirementStep)
                .step(UpdateItemRequirementProgressStep)
                .step(CheckTaskCompletionStep)
        }
    }
}
