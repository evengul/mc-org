package app.mcorg.presentation.handler

import app.mcorg.domain.model.projects.Project
import app.mcorg.domain.model.task.TaskSpecification
import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.project.GetProjectAssignmentInputStep
import app.mcorg.pipeline.project.GetProjectAssignmentInputStepFailure
import app.mcorg.pipeline.project.GetProjectStep
import app.mcorg.pipeline.project.GetProjectStepFailure
import app.mcorg.pipeline.project.GetWorldUsersForProjects
import app.mcorg.pipeline.project.GetWorldUsersForProjectsFailure
import app.mcorg.pipeline.task.AssignTaskFailure
import app.mcorg.pipeline.task.AssignTaskOrRemoveTaskAssignmentStep
import app.mcorg.pipeline.task.ConvertMaterialListStep
import app.mcorg.pipeline.task.CountableTaskValidator
import app.mcorg.pipeline.task.CreateCountableTaskFailure
import app.mcorg.pipeline.task.CreateCountableTaskStep
import app.mcorg.pipeline.task.CreateDoableTaskFailure
import app.mcorg.pipeline.task.CreateDoableTaskStep
import app.mcorg.pipeline.task.CreateLitematicaTasksFailure
import app.mcorg.pipeline.task.CreateLitematicaTasksStep
import app.mcorg.pipeline.task.DeleteTaskFailure
import app.mcorg.pipeline.task.DeleteTaskStep
import app.mcorg.pipeline.task.EditDoneMoreTaskFailure
import app.mcorg.pipeline.task.GetCountableTaskDoneMoreInputStep
import app.mcorg.pipeline.task.GetCountableTaskInputStep
import app.mcorg.pipeline.task.GetCountableTasksEditInputStep
import app.mcorg.pipeline.task.GetDoableTaskInputStep
import app.mcorg.pipeline.task.GetLitematicaTasksInputStep
import app.mcorg.pipeline.task.GetTaskStageInputStep
import app.mcorg.pipeline.task.GetTasksFailure
import app.mcorg.pipeline.task.LitematicaTasksValidator
import app.mcorg.pipeline.task.UpdateCountableTaskDoneStep
import app.mcorg.pipeline.task.UpdateCountableTaskRequirementsStep
import app.mcorg.pipeline.task.UpdateTaskRequirementsFailure
import app.mcorg.pipeline.task.UpdateTaskStageFailure
import app.mcorg.pipeline.task.UpdateTaskStageStep
import app.mcorg.pipeline.task.ValidateCountableTaskRequirementsStep
import app.mcorg.pipeline.task.ValidateMaterialListStep
import app.mcorg.pipeline.task.ValidateTaskNameStep
import app.mcorg.presentation.utils.url.URLMappers
import app.mcorg.presentation.templates.task.board.createTaskBoard
import app.mcorg.presentation.utils.*
import app.mcorg.presentation.utils.url.taskFilterURLMapper
import io.ktor.http.*
import io.ktor.http.content.MultiPartData
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handlePostDoableTask() {
    val projectId = getProjectId()
    Pipeline.create<CreateDoableTaskFailure, Parameters>()
        .pipe(GetDoableTaskInputStep)
        .pipe(ValidateTaskNameStep)
        .pipe(CreateDoableTaskStep(projectId))
        .map { projectId }
        .andThen(getRefreshTasksPipeline())
        .fold(
            input = receiveParameters(),
            onSuccess = { respondHtml(it) },
            onFailure = { respond(HttpStatusCode.InternalServerError, "An unknown error occurred") }
        )
}

suspend fun ApplicationCall.handlePostCountableTask() {
    val projectId = getProjectId()
    Pipeline.create<CreateCountableTaskFailure, Parameters>()
        .pipe(GetCountableTaskInputStep)
        .pipe(CountableTaskValidator)
        .pipe(CreateCountableTaskStep(projectId))
        .map { projectId }
        .andThen(getRefreshTasksPipeline())
        .fold(
            input = receiveParameters(),
            onSuccess = { respondHtml(it) },
            onFailure = { respond(HttpStatusCode.InternalServerError, "An unknown error occurred") }
        )
}

suspend fun ApplicationCall.handlePostLitematicaTasks() {
    val projectId = getProjectId()

    Pipeline.create<CreateLitematicaTasksFailure, MultiPartData>()
        .pipe(GetLitematicaTasksInputStep)
        .pipe(ValidateMaterialListStep)
        .pipe(ConvertMaterialListStep)
        .pipe(LitematicaTasksValidator)
        .pipe(CreateLitematicaTasksStep(projectId))
        .map { projectId }
        .andThen(getRefreshTasksPipeline())
        .fold(
            input = receiveMultipart(),
            onSuccess = { respondHtml(it) },
            onFailure = { respond(HttpStatusCode.InternalServerError, "An unknown error occurred") }
        )
}

suspend fun ApplicationCall.handleDeleteTask() {
    val projectId = getProjectId()
    val taskId = getTaskId()

    Pipeline.create<DeleteTaskFailure, Int>()
        .pipe(DeleteTaskStep)
        .map { projectId }
        .andThen(getRefreshTasksPipeline())
        .fold(
            input = taskId,
            onSuccess = { respondHtml(it) },
            onFailure = { respond(HttpStatusCode.InternalServerError, "An unknown error occurred") }
        )
}

suspend fun ApplicationCall.handlePatchCountableTaskDoneMore() {
    val projectId = getProjectId()
    val taskId = getTaskId()

    Pipeline.create<EditDoneMoreTaskFailure, Parameters>()
        .pipe(GetCountableTaskDoneMoreInputStep)
        .pipe(ValidateCountableTaskRequirementsStep)
        .pipe(UpdateCountableTaskDoneStep(taskId))
        .map { projectId }
        .andThen(getRefreshTasksPipeline())
        .fold(
            input = parameters,
            onSuccess = { respondHtml(it) },
            onFailure = { respond(HttpStatusCode.InternalServerError, "An unknown error occurred") }
        )
}

suspend fun ApplicationCall.handleEditTaskRequirements() {
    val projectId = getProjectId()
    Pipeline.create<UpdateTaskRequirementsFailure, Parameters>()
        .pipe(GetCountableTasksEditInputStep)
        .pipe(UpdateCountableTaskRequirementsStep)
        .map { projectId }
        .andThen(getRefreshTasksPipeline())
        .fold(
            input = receiveParameters(),
            onSuccess = { respondHtml(it) },
            onFailure = { respond(HttpStatusCode.InternalServerError, "An unknown error occurred") }
        )
}

suspend fun ApplicationCall.handlePatchTaskAssignee() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val taskId = getTaskId()

    Pipeline.create<AssignTaskFailure, Parameters>()
        .wrapPipe(GetProjectAssignmentInputStep) {
            when(it) {
                is Result.Failure -> when (it.error) {
                    is GetProjectAssignmentInputStepFailure.UserIdNotPresent -> Result.failure(AssignTaskFailure.UserIdNotPresent)
                }
                is Result.Success -> Result.success(it.value)
            }
        }
        .pipe(AssignTaskOrRemoveTaskAssignmentStep(worldId, taskId))
        .map { projectId }
        .andThen(getRefreshTasksPipeline())
        .fold(
            input = receiveParameters(),
            onSuccess = { respondHtml(it) },
            onFailure = { respond(HttpStatusCode.InternalServerError, "An unknown error occurred") }
        )
}

suspend fun ApplicationCall.handleUpdateTaskStage() {
    val taskId = getTaskId()

    Pipeline.create<UpdateTaskStageFailure, Parameters>()
        .pipe(GetTaskStageInputStep)
        .pipe(UpdateTaskStageStep(taskId))
        .fold(
            input = parameters,
            onSuccess = { respond(HttpStatusCode.NoContent) },
            onFailure = { respond(HttpStatusCode.InternalServerError, "An unknown error occurred") }
        )
}

data class GetTasksData(
    var project: Project? = null,
    var specification: TaskSpecification = TaskSpecification.default(),
    var users: List<User> = emptyList<User>()
)

private fun ApplicationCall.getRefreshTasksPipeline(): Pipeline<Int, GetTasksFailure, String> {
    val stepData = GetTasksData()
    val worldId = getWorldId()
    val currentUser = getUser()

    return Pipeline.create<GetTasksFailure, Int>()
        .wrapPipe(GetProjectStep(GetProjectStep.Include.onlyTasks())) {
            when (it) {
                is Result.Failure -> when (it.error) {
                    is GetProjectStepFailure.NotFound -> Result.failure(GetTasksFailure.ProjectNotFound)
                    is GetProjectStepFailure.Other -> Result.failure(GetTasksFailure.Other(it.error.failure))
                }

                is Result.Success -> Result.success(it.value)
            }
        }
        .peek { stepData.project = it }
        .map { URLMappers.taskFilterURLMapper(getCurrentUrl()) }
        .peek { stepData.specification = it }
        .map { currentUser }
        .wrapPipe(GetWorldUsersForProjects(worldId)) {
            when (it) {
                is Result.Failure -> when (it.error) {
                    is GetWorldUsersForProjectsFailure.Other -> Result.failure(GetTasksFailure.Other(it.error.failure))
                }

                is Result.Success -> Result.success(it.value)
            }
        }
        .peek { stepData.users = it }
        .map {
            hxTarget("#task-board")
            hxSwap("outerHTML")
            val filteredProject = filterAndSortProject(currentUser.id, stepData.project!!, stepData.specification)
            createTaskBoard(filteredProject, stepData.users, currentUser)
        }
}