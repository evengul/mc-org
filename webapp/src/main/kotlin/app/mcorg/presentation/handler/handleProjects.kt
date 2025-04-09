package app.mcorg.presentation.handler

import app.mcorg.domain.cqrs.commands.project.AddProjectAssignmentCommand
import app.mcorg.domain.cqrs.commands.project.RemoveProjectAssignmentCommand
import app.mcorg.domain.model.projects.*
import app.mcorg.domain.model.users.Profile
import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.project.*
import app.mcorg.presentation.configuration.ProjectCommands
import app.mcorg.presentation.configuration.permissionsApi
import app.mcorg.presentation.configuration.projectsApi
import app.mcorg.presentation.entities.user.AssignUserRequest
import app.mcorg.presentation.entities.user.DeleteAssignmentRequest
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.mappers.InputMappers
import app.mcorg.presentation.mappers.URLMappers
import app.mcorg.presentation.mappers.project.projectFilterURLMapper
import app.mcorg.presentation.mappers.task.taskFilterInputMapper
import app.mcorg.presentation.mappers.user.assignUserInputMapper
import app.mcorg.presentation.templates.project.*
import app.mcorg.presentation.utils.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.stream.createHTML

data class GetProjectsData(
    val worldId: Int,
    val specification: ProjectSpecification = ProjectSpecification.default(),
    val totalProjectCount: Int = 0,
    val projects: List<SlimProject> = emptyList(),
    val users: List<User> = emptyList(),
    val currentUserProfile: Profile = Profile.default(),
)

suspend fun ApplicationCall.handleGetProjects() {
    val worldId = getWorldId()
    val userId = getUserId()

    var stepState = GetProjectsData(worldId = worldId)

    Pipeline.create<GetProjectsFailure, Parameters>()
        .pipe(GetProjectSpecificationFromFormStep)
        .peek { stepState = stepState.copy(specification = it) }
        .pipe(GetSpecifiedProjectsStep(worldId))
        .peek { stepState = stepState.copy(totalProjectCount = it.first, projects = it.second) }
        .map { }
        .pipe(GetProfileForProjects(userId))
        .peek { stepState = stepState.copy(currentUserProfile = it) }
        .map { it.toUser() }
        .pipe(GetWorldUsersForProjects(worldId))
        .peek { stepState = stepState.copy(users = it) }
        .map { projects(stepState.worldId, stepState.projects, stepState.users, stepState.currentUserProfile, stepState.specification, stepState.totalProjectCount) }
        .fold(
            input = parameters,
            onFailure = { respond(HttpStatusCode.InternalServerError, "Unknown error occurred") },
            onSuccess = { respondHtml(it) }
        )
}

data class PostProjectData(
    val specification: ProjectSpecification = ProjectSpecification.default(),
    val createdProject: SlimProject? = null,
    val totalProjectCount: Int = 0,
    val filteredProjectCount: Int = 0,
)

suspend fun ApplicationCall.handlePostProject() {
    val worldId = getWorldId()
    val user = getUser()

    var stepState = PostProjectData()

    Pipeline.create<CreateProjectFailure, Parameters>()
        .pipe(GetCreateProjectInputStep)
        .pipe(ValidateCreateProjectInputStep(worldId))
        .pipe(CreateProjectStep(worldId))
        .pipe(GetProjectStep(GetProjectStep.Include.none()))
        .peek { stepState = stepState.copy(createdProject = it.toSlim()) }
        .map { URLMappers.projectFilterURLMapper(request.headers["HX-Current-URL"]) }
        .peek { stepState = stepState.copy(specification = it) }
        .pipe(GetProjectCountWithFilteredCount(worldId))
        .peek { stepState = stepState.copy(totalProjectCount = it.first, filteredProjectCount = it.second) }
        .map { user }
        .pipe(GetWorldUsersForProjects(worldId))
        .map {
            val project = stepState.createdProject
            if (project != null) {
                val projectListElement = createProjectListElement(
                    worldId,
                    project,
                    it,
                    user
                )
                if (project.matches(stepState.specification)) {
                    if (stepState.filteredProjectCount != stepState.totalProjectCount) {
                        return@map createHTML().p {
                            oobFilteredProjectsDisplay(stepState.filteredProjectCount, stepState.totalProjectCount)
                        } + "\n" + projectListElement
                    }
                    return@map projectListElement
                }
                return@map createHTML().p {
                    oobFilteredProjectsDisplay(stepState.filteredProjectCount, stepState.totalProjectCount)
                }
            }
            return@map ""
        }
        .fold(
            input = receiveParameters(),
            onSuccess = { respondHtml(it) },
            onFailure = { respond(HttpStatusCode.InternalServerError, "Unknown error occurred") }
        )
}

suspend fun ApplicationCall.handleDeleteProject() {
    val worldId = getWorldId()
    val projectId = getProjectId()

    Pipeline.create<Any, Int>()
        .pipe(DeleteProjectStep)
        .map { URLMappers.projectFilterURLMapper(request.headers["HX-Current-URL"]) }
        .pipe(GetProjectCountWithFilteredCount(worldId))
        .map {
            if (it.first != it.second) {
                createHTML().p {
                    oobFilteredProjectsDisplay(it.second, it.first)
                }
            } else ""
        }
        .fold(
            input = projectId,
            onSuccess = { respondHtml(it) },
            onFailure = { respond(HttpStatusCode.InternalServerError, "Unknown error occurred") }
        )
}

suspend fun ApplicationCall.handleGetProject() {
    val userId = getUserId()
    val worldId = getWorldId()
    val projectId = getProjectId()
    val currentUser = getUser()
    val users = permissionsApi.getUsersInWorld(worldId)
    val project = projectsApi.getProject(projectId, includeTasks = true, includeDependencies = false)
        ?: throw IllegalArgumentException("Project not found")

    val taskSpecification = InputMappers.taskFilterInputMapper(parameters)

    respondHtml(
        project(
            "/app/worlds/${getWorldId()}/projects",
            project.filterSortTasks(taskSpecification, userId),
            users,
            currentUser,
            taskSpecification
        )
    )
}

suspend fun ApplicationCall.handlePatchProjectAssignee() {
    when (val request = InputMappers.assignUserInputMapper(receiveParameters())) {
        is DeleteAssignmentRequest -> handleDeleteProjectAssignee()
        is AssignUserRequest -> handleAssignProjectAssignee(request.userId)
    }
}

suspend fun ApplicationCall.handleDeleteProjectAssignee() {
    val projectId = getProjectId()

    ProjectCommands.removeAssignment(projectId).fold(
        {
            when(it) {
                is RemoveProjectAssignmentCommand.ProjectDoesNotHaveAssignedUser -> respondBadRequest("Project does not have assigned user")
            }
        },
        {
            val worldId = getWorldId()
            val worldUsers = permissionsApi.getUsersInWorld(worldId)
            handleEditProjectAssignee(projectId, worldUsers)
        }
    )
}

suspend fun ApplicationCall.handleAssignProjectAssignee(userId: Int) {
    val worldId = getWorldId()
    val projectId = getProjectId()

    ProjectCommands.assign(worldId, projectId, userId).fold(
        {
            when(it) {
                is AddProjectAssignmentCommand.UserDoesNotExistInWorldFailure -> respondNotFound("User does not exist in world and cannot be assigned to project")
            }
        },
        {
            val worldUsers = permissionsApi.getUsersInWorld(worldId)
            handleEditProjectAssignee(projectId, worldUsers)
        }
    )
}

private suspend fun ApplicationCall.handleEditProjectAssignee(projectId: Int, worldUsers: List<User>) {
    val worldId = getWorldId()
    val currentUser = getUser()
    val project =
        projectsApi.getProject(projectId)?.toSlim() ?: throw IllegalArgumentException("Project does not exist")
    val filter = URLMappers.projectFilterURLMapper(request.headers["HX-Current-URL"])
    if (project.matches(filter)) {
        respondHtml(
            getFilteredAndTotalProjectBasedOnFilter(worldId, this.request) + "\n" + createAssignProject(
                project,
                worldUsers,
                currentUser
            )
        )
    } else {
        hxTarget("#project-${project.id}")
        hxSwap("outerHTML")
        respondHtml(createHTML().div {
            id = "project-${project.id}"
            hxOutOfBands("delete")
        } + "\n" + getFilteredAndTotalProjectBasedOnFilter(worldId, this.request))
    }
}

private fun getFilteredAndTotalProjectBasedOnFilter(worldId: Int, request: ApplicationRequest): String {
    val specification = URLMappers.projectFilterURLMapper(request.headers["HX-Current-URL"])
    val allProjects = projectsApi.getWorldProjects(worldId)
    val filteredProjects = allProjects.filter { it.matches(specification) }.size
    if (filteredProjects != allProjects.size) {
        return createHTML().p {
            oobFilteredProjectsDisplay(filteredProjects, allProjects.size)
        }
    }
    return ""
}