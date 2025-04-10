package app.mcorg.presentation.handler

import app.mcorg.domain.model.projects.*
import app.mcorg.domain.model.task.TaskSpecification
import app.mcorg.domain.model.users.Profile
import app.mcorg.domain.model.users.User
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.project.*
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.mappers.InputMappers
import app.mcorg.presentation.mappers.URLMappers
import app.mcorg.presentation.mappers.project.projectFilterURLMapper
import app.mcorg.presentation.mappers.task.taskFilterInputMapper
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
        .pipe(GetProjectSpecificationFromFormStep) { stepState = stepState.copy(specification = it) }
        .pipe(GetSpecifiedProjectsStep(worldId)) { stepState = stepState.copy(totalProjectCount = it.first, projects = it.second) }
        .pipe(Step.value(Unit))
        .pipe(GetProfileForProjects(userId)) { stepState = stepState.copy(currentUserProfile = it) }
        .map { it.toUser() }
        .pipe(GetWorldUsersForProjects(worldId)) { stepState = stepState.copy(users = it) }
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
        .pipe(GetProjectStep(GetProjectStep.Include.none())) { stepState = stepState.copy(createdProject = it.toSlim()) }
        .map { URLMappers.projectFilterURLMapper(getCurrentUrl()) }
        .peek { stepState = stepState.copy(specification = it) }
        .pipe(GetProjectCountWithFilteredCount(worldId)) { stepState = stepState.copy(totalProjectCount = it.first, filteredProjectCount = it.second) }
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

    Pipeline.create<DeleteProjectFailure, Int>()
        .pipe(DeleteProjectStep)
        .map { URLMappers.projectFilterURLMapper(getCurrentUrl()) }
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

    var stepData = Pair<TaskSpecification, Project?>(
        TaskSpecification.default(),
        null,
    )

    Pipeline.create<GetProjectFailure, Int>()
        .pipe(GetProjectStep(GetProjectStep.Include.onlyTasks()))
        .map {
            val taskSpecification = InputMappers.taskFilterInputMapper(parameters)
            stepData = stepData.copy(first = taskSpecification)
            it.filterSortTasks(taskSpecification, userId)
        }
        .peek { stepData = stepData.copy(second = it) }
        .pipe(Step.value(currentUser))
        .pipe(GetWorldUsersForProjects(worldId))
        .map {
            project(
                "/app/worlds/${worldId}/projects",
                stepData.second!!,
                it,
                currentUser,
                stepData.first
            )
        }
        .fold(
            input = projectId,
            onSuccess = { respondHtml(it) },
            onFailure = { respond(HttpStatusCode.InternalServerError, "Unknown error occurred") }
        )
}

data class EditProjectAssigneeData(
    var project: SlimProject? = null,
    var matches: Boolean = false,
    var counts: Pair<Int, Int> = 0 to 0,
    var users: List<User> = emptyList(),
)

suspend fun ApplicationCall.handlePatchProjectAssignee() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val currentUser = getUser()

    val stepData = EditProjectAssigneeData()

    Pipeline.create<AssignProjectFailure, Parameters>()
        .pipe(GetProjectAssignmentInputStep)
        .pipe(AssignProjectOrRemoveProjectAssignmentStep(worldId, projectId))
        .map { projectId }
        .pipe(GetProjectStep(GetProjectStep.Include.none())) { stepData.project = it.toSlim() }
        .map { URLMappers.projectFilterURLMapper(getCurrentUrl()) }
        .peek { stepData.matches = stepData.project!!.matches(it) }
        .pipe(GetProjectCountWithFilteredCount(worldId)) { stepData.counts = it }
        .map { currentUser }
        .pipe(GetWorldUsersForProjects(worldId)) { stepData.users = it }
        .map {
            val countsElement = stepData.counts.takeIf { it.first != it.second }?.let { counts ->
                createHTML().p {
                    oobFilteredProjectsDisplay(counts.second, counts.first)
                }
            } ?: ""

            if (stepData.matches) {
                val projectListElement = stepData.project?.let { project -> createAssignProject(
                    project,
                    stepData.users,
                    currentUser
                ) } ?: ""

                return@map listOf(projectListElement, countsElement).joinToString("\n")
            }

            hxTarget("#project-${stepData.project!!.id}")
            hxSwap("outerHTML")
            createHTML().div {
                id = "project-${stepData.project!!.id}"
                hxOutOfBands("delete")
            } + "\n" + countsElement
        }
        .fold(
            input = receiveParameters(),
            onSuccess = { respondHtml(it) },
            onFailure = { respond(HttpStatusCode.InternalServerError, "Unknown error occurred") }
        )
}