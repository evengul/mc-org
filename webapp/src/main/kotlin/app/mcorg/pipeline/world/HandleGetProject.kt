package app.mcorg.pipeline.world

import app.mcorg.domain.model.project.toProjectResourceGathering
import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.user.Role
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
import app.mcorg.presentation.templated.project.projectPage
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.utils.respondNotFound
import app.mcorg.presentation.utils.respondBadRequest
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.project.CountTotalTasksStep
import app.mcorg.pipeline.project.ProjectDependencyData
import app.mcorg.pipeline.project.dependencies.GetAvailableProjectDependenciesStep
import app.mcorg.pipeline.project.resources.GetItemsInWorldVersionStep
import app.mcorg.presentation.templated.project.projectTabsContent
import app.mcorg.presentation.utils.BreadcrumbBuilder
import app.mcorg.presentation.utils.getWorldId
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetProject() {
    val user = this.getUser()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val tab = request.queryParameters["tab"]?.let {
        when (it) {
            "tasks", "resources", "location", "stages", "dependencies", "settings" -> it
            else -> null // Invalid tab, return null
        }
    }

    // Get project with access validation
    val project = when (val projectResult = GetProjectByIdStep.process(projectId)) {
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
    val itemNames = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    val tabData = when (tab) {
        "resources" -> {
            // Get resource production data
            val gatheringTasks = GetTasksByProjectIdStep.process(
                GetTasksByProjectIdInput(projectId, includeCompleted = true),
            ).getOrNull() ?: emptyList()
            val resourceProductionResult = GetResourceProductionStep.process(GetResourceProductionInput(projectId))
            val resourceProduction = when (resourceProductionResult) {
                is Result.Success -> resourceProductionResult.getOrNull()!!
                is Result.Failure -> emptyList()
            }

            ProjectTab.Resources(
                project,
                gatheringTasks
                    .flatMap { it.requirements }
                    .filterIsInstance<ItemRequirement>()
                    .toProjectResourceGathering(),
                resourceProduction,
                itemNames
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
                is Result.Failure -> ProjectDependencyData(emptyList(), emptyList())
            }

            val availableProjects = when(val result = GetAvailableProjectDependenciesStep(worldId).process(projectId)) {
                is Result.Success -> result.value
                is Result.Failure -> emptyList()
            }

            ProjectTab.Dependencies(
                project,
                availableProjects,
                dependencyData.dependencies,
                dependencyData.dependents
            )
        }

        "location" -> ProjectTab.Location(project)

        "settings" -> {
            val worldMemberRole = worldMemberQueryStep.process(
                WorldMemberQueryInput(user.id, worldId)
            ).getOrNull()?.worldRole ?: Role.MEMBER
            ProjectTab.Settings(project, worldMemberRole)
        }

        else -> {
            val tasks = when (val tasksResult = GetTasksByProjectIdStep.process(GetTasksByProjectIdInput(projectId, includeCompleted = false))) {
                is Result.Success -> tasksResult.getOrNull()!!
                is Result.Failure -> {
                    respondBadRequest("Failed to load tasks")
                    return
                }
            }
            val totalCount = CountTotalTasksStep.process(projectId).getOrNull() ?: tasks.size
            ProjectTab.Tasks(project, totalCount, tasks)
        }
    }

    if (request.headers["HX-Request"] == "true" && tab != null) {
        respondHtml(createHTML().div {
            projectTabsContent(tabData)
        })
        return
    }

    val breadcrumbs = BreadcrumbBuilder.buildForProject(
        worldId = worldId,
        projectName = project.name
    )

    respondHtml(projectPage(user, tabData, itemNames, unreadNotifications, breadcrumbs))
}