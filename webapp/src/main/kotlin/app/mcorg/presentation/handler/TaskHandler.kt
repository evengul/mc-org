package app.mcorg.presentation.handler

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.task.*
import app.mcorg.pipeline.project.GetProjectByIdInput
import app.mcorg.pipeline.project.GetProjectByIdStep
import app.mcorg.presentation.templated.project.emptyTasksDisplay
import app.mcorg.presentation.templated.project.requirement
import app.mcorg.presentation.templated.project.taskCompletionCheckbox
import app.mcorg.presentation.templated.project.taskItem
import app.mcorg.presentation.templated.project.taskProgressDisplay
import app.mcorg.presentation.templated.project.tasksList
import app.mcorg.presentation.utils.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.http.Parameters
import io.ktor.server.response.respond
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
                } + createHTML().div {
                    attributes["hx-swap-oob"] = "delete:.project-tasks-empty"
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
                if (result.tasks.isEmpty()) {
                    respondHtml(createHTML().p {
                        classes += "subtle"
                        +"No tasks found matching the search criteria."
                    })
                } else {
                    respondHtml(createHTML().ul {
                        tasksList(worldId, projectId, result.tasks)
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
            onSuccess = { result: CompleteTaskResult ->
                val task = result.updatedTasks.find { it.id == taskId }
                if (task == null) {
                    respond(HttpStatusCode.InternalServerError)
                    return@executePipeline
                }
                respondHtml(createHTML().li {
                    taskItem(worldId, projectId, task)
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
                .step(ValidateTaskCompletionStep)
                .step(CompleteTaskStep)
                .step(GetUpdatedTasksAfterCompletionStep)
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
                if (result.updatedTasks.isNotEmpty()) {
                    respond(HttpStatusCode.OK)
                } else {
                    val project = GetProjectByIdStep.process(
                        GetProjectByIdInput(projectId, user.id)
                    ).getOrNull()

                    if (project == null) {
                        respond(HttpStatusCode.InternalServerError, "Failed to load project after task deletion.")
                    } else {
                        respondHtml(createHTML().div {
                            attributes["hx-swap-oob"] = "beforebegin:#tasks-list"
                            div {
                                emptyTasksDisplay(project)
                            }
                        })
                    }
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
        val requirementId = this.parameters["requirementId"]?.toIntOrNull()
            ?: return respondBadRequest("Invalid requirement ID")

        executePipeline(
            onSuccess = { result: UpdateRequirementProgressResult ->
                respondHtml(respondUpdateTaskResult(worldId, projectId, taskId, result))
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
                .step(InjectRequirementContextStep(requirementId, taskId, projectId, user.id))
                .step(GetRequirementStep)
                .step(UpdateItemRequirementProgressStep)
                .step(Step.value { taskId to it })
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
                respondHtml(respondUpdateTaskResult(worldId, projectId, taskId, result))
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
                .step(InjectRequirementContextStep(requirementId, taskId, projectId, user.id))
                .step(GetRequirementStep)
                .step(UpdateItemRequirementProgressStep)
                .step(Step.value { taskId to it })
                .step(CheckTaskCompletionStep)
        }
    }

    private fun respondUpdateTaskResult(worldId: Int, projectId: Int, taskId: Int, result: UpdateRequirementProgressResult) = createHTML().li {
        requirement(result.requirement, worldId, projectId, taskId)
    } + createHTML().span {
        attributes["hx-swap-oob"] = "true"
        attributes["hx-target"] = "#task-$taskId-progress"
        taskProgressDisplay(taskId, result.taskProgress * 100.0)
    } + createHTML().input {
        attributes["hx-swap-oob"] = "true"
        attributes["hx-target"] = "#task-$taskId-complete"
        taskCompletionCheckbox(worldId, projectId, taskId, result.taskProgress >= 1.0)
    }
}
