package app.mcorg.pipeline.project

import app.mcorg.domain.model.project.toProjectResourceGathering
import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.notification.getUnreadNotificationsOrZero
import app.mcorg.pipeline.project.commonsteps.GetProjectByIdStep
import app.mcorg.pipeline.project.dependencies.GetAvailableProjectDependenciesStep
import app.mcorg.pipeline.project.dependencies.GetProjectDependenciesStep
import app.mcorg.pipeline.project.resources.GetItemsInWorldVersionStep
import app.mcorg.pipeline.project.resources.GetResourceProductionStep
import app.mcorg.pipeline.task.SearchTasksInput
import app.mcorg.pipeline.task.SearchTasksStep
import app.mcorg.pipeline.task.commonsteps.CountProjectTasksStep
import app.mcorg.pipeline.world.commonsteps.GetWorldMemberStep
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.templated.project.*
import app.mcorg.presentation.utils.*
import io.ktor.server.application.*
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

    val createTaskTab = request.queryParameters["requirementTab"]?.let { when(it) {
        "item" -> CreateTaskModalTab.ITEM_REQUIREMENT
        "action" -> CreateTaskModalTab.ACTION_REQUIREMENT
        else -> null
    } }

    val itemNames = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    if (createTaskTab != null && request.headers["HX-Request"] == "true") {
        respondHtml(createHTML().div {
            when(createTaskTab) {
                CreateTaskModalTab.ITEM_REQUIREMENT -> itemRequirementForm(itemNames)
                CreateTaskModalTab.ACTION_REQUIREMENT -> actionRequirementForm()
            }
        })
        return
    }

    // Get project with access validation
    val project = when (val projectResult = GetProjectByIdStep.process(projectId)) {
        is Result.Success -> projectResult.value
        is Result.Failure -> {
            defaultHandleError(projectResult.error)
            return
        }
    }

    val unreadNotifications = getUnreadNotificationsOrZero(user.id)

    val tabData = when (tab) {
        "resources" -> {
            // Get resource production data
            val gatheringTasks = SearchTasksStep(projectId).process(
                SearchTasksInput(
                    completionStatus = "ALL"
                )
            ).getOrNull() ?: emptyList()
            val resourceProduction = when (val result = GetResourceProductionStep.process(projectId)) {
                is Result.Success -> result.value
                is Result.Failure -> emptyList()
            }

            ProjectTab.Resources(
                project,
                user,
                gatheringTasks
                    .filter { it.requirement is ItemRequirement }
                    .toProjectResourceGathering(),
                resourceProduction,
                itemNames
            )
        }

        "dependencies" -> {
            // Get project dependencies
            val dependencyData = when (val dependenciesResult = GetProjectDependenciesStep(projectId).process(Unit)) {
                is Result.Success -> dependenciesResult.value
                is Result.Failure -> emptyList()
            }

            val availableProjects = when(val result = GetAvailableProjectDependenciesStep(worldId).process(projectId)) {
                is Result.Success -> result.value
                is Result.Failure -> emptyList()
            }

            ProjectTab.Dependencies(
                project,
                user,
                availableProjects,
                dependencyData.filter { it.dependentId == projectId },
                dependencyData.filter { it.dependencyId == projectId }
            )
        }

        "location" -> ProjectTab.Location(project, user)

        "settings" -> {
            val worldMemberRole = GetWorldMemberStep(worldId).process(
                user.id
            ).getOrNull()?.worldRole ?: Role.MEMBER
            ProjectTab.Settings(project, user, worldMemberRole)
        }

        else -> {
            val tasks = when (val tasksResult = SearchTasksStep(projectId).process(SearchTasksInput())) {
                is Result.Success -> tasksResult.value
                is Result.Failure -> {
                    respondBadRequest("Failed to load tasks")
                    return
                }
            }
            val totalCount = CountProjectTasksStep.process(projectId).getOrNull() ?: tasks.size
            ProjectTab.Tasks(project, user, totalCount, tasks)
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