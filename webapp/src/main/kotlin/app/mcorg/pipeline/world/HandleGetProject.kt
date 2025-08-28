package app.mcorg.pipeline.world

import app.mcorg.domain.model.project.toProjectResourceGathering
import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.pipeline.project.GetProjectByIdInput
import app.mcorg.pipeline.project.GetProjectByIdStep
import app.mcorg.pipeline.project.GetTasksByProjectIdInput
import app.mcorg.pipeline.project.GetTasksByProjectIdStep
import app.mcorg.pipeline.project.GetProjectDependenciesInput
import app.mcorg.pipeline.project.GetProjectDependenciesStep
import app.mcorg.pipeline.project.GetResourceProductionInput
import app.mcorg.pipeline.project.GetResourceProductionStep
import app.mcorg.pipeline.project.GetProjectStageHistoryInput
import app.mcorg.pipeline.project.GetProjectStageHistoryStep
import app.mcorg.presentation.templated.layout.topbar.getUnreadNotificationCount
import app.mcorg.presentation.templated.project.ProjectTab
import app.mcorg.presentation.templated.project.dependenciesTab
import app.mcorg.presentation.templated.project.locationTab
import app.mcorg.presentation.templated.project.projectPage
import app.mcorg.presentation.templated.project.resourcesTab
import app.mcorg.presentation.templated.project.stagesTab
import app.mcorg.presentation.templated.project.tasksTab
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.utils.respondNotFound
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.domain.pipeline.Result
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetProject() {
    val user = this.getUser()
    val projectId = this.getProjectId()
    val tab = request.queryParameters["tab"]?.let {
        when (it) {
            "tasks", "resources", "location", "stages", "dependencies" -> it
            else -> null // Invalid tab, return null
        }
    }

    // Get project with access validation
    val project = when (val projectResult = GetProjectByIdStep.process(GetProjectByIdInput(projectId, user.id))) {
        is Result.Success -> projectResult.getOrNull()!!
        is Result.Failure -> {
            when (projectResult.error) {
                is app.mcorg.pipeline.project.GetProjectByIdFailures.ProjectNotFound -> {
                    respondNotFound("Project not found")
                    return
                }
                is app.mcorg.pipeline.project.GetProjectByIdFailures.AccessDenied -> {
                    respondBadRequest("Access denied")
                    return
                }
                is app.mcorg.pipeline.project.GetProjectByIdFailures.DatabaseError -> {
                    respondBadRequest("Database error")
                    return
                }
            }
        }
    }

    val unreadNotifications = getUnreadNotificationCount(user)

    // Get tasks for the project
    val tasks = when (val tasksResult = GetTasksByProjectIdStep.process(GetTasksByProjectIdInput(projectId))) {
        is Result.Success -> tasksResult.getOrNull()!!
        is Result.Failure -> {
            respondBadRequest("Failed to load tasks")
            return
        }
    }

    val tabData = when (tab) {
        "resources" -> {
            // Get resource production data
            val resourceProductionResult = GetResourceProductionStep.process(GetResourceProductionInput(projectId))
            val resourceProduction = when (resourceProductionResult) {
                is Result.Success -> resourceProductionResult.getOrNull()!!
                is Result.Failure -> emptyList()
            }

            ProjectTab.Resources(
                project,
                tasks
                    .flatMap { it.requirements }
                    .filterIsInstance<ItemRequirement>()
                    .toProjectResourceGathering(),
                resourceProduction
            )
        }

        "stages" -> {
            // Get stage history
            val stageHistoryResult = GetProjectStageHistoryStep.process(GetProjectStageHistoryInput(projectId))
            val stageChanges = when (stageHistoryResult) {
                is Result.Success -> stageHistoryResult.getOrNull()!!
                is Result.Failure -> emptyList()
            }

            ProjectTab.Stages(project, stageChanges)
        }

        "dependencies" -> {
            // Get project dependencies
            val dependenciesResult = GetProjectDependenciesStep.process(GetProjectDependenciesInput(projectId))
            val dependencyData = when (dependenciesResult) {
                is Result.Success -> dependenciesResult.getOrNull()!!
                is Result.Failure -> app.mcorg.pipeline.project.ProjectDependencyData(emptyList(), emptyList())
            }

            ProjectTab.Dependencies(
                project,
                dependencyData.dependencies,
                dependencyData.dependents
            )
        }

        "location" -> ProjectTab.Location(project)

        else -> ProjectTab.Tasks(project, tasks)
    }

    if (request.headers["HX-Request"] == "true" && tab != null) {
        handleGetTab(tabData)
        return
    }

    respondHtml(projectPage(user, tabData, unreadNotifications))
}

suspend fun ApplicationCall.handleGetTab(tabData: ProjectTab) {
    respondHtml(createHTML().div {
        when (tabData) {
            is ProjectTab.Tasks -> tasksTab(tabData.project, tabData.tasks)
            is ProjectTab.Dependencies -> dependenciesTab(tabData.dependencies, tabData.dependents)
            is ProjectTab.Location -> locationTab(tabData.project)
            is ProjectTab.Resources -> resourcesTab(
                tabData.project,
                tabData.resourceProduction,
                tabData.resourceGathering
            )

            is ProjectTab.Stages -> stagesTab(tabData.stageChanges)
        }
    })
}