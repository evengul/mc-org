package app.mcorg.pipeline.world

import app.mcorg.domain.model.project.toProjectResourceGathering
import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.project.GetProjectByIdStep
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
import app.mcorg.pipeline.project.ProjectDependencyData
import app.mcorg.pipeline.project.dependencies.GetAvailableProjectDependenciesStep
import app.mcorg.pipeline.project.resources.GetItemsInWorldVersionStep
import app.mcorg.pipeline.task.CountProjectTasksStep
import app.mcorg.pipeline.task.SearchTasksInput
import app.mcorg.pipeline.task.SearchTasksStep
import app.mcorg.presentation.templated.project.CreateTaskModalTab
import app.mcorg.presentation.templated.project.actionRequirementForm
import app.mcorg.presentation.templated.project.itemRequirementForm
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

    val tabData = when (tab) {
        "resources" -> {
            // Get resource production data
            val gatheringTasks = SearchTasksStep.process(
                SearchTasksInput(
                    projectId,
                    userId = user.id,
                    completionStatus = "ALL"
                )
            ).getOrNull()?.tasks ?: emptyList()
            val resourceProductionResult = GetResourceProductionStep.process(GetResourceProductionInput(projectId))
            val resourceProduction = when (resourceProductionResult) {
                is Result.Success -> resourceProductionResult.getOrNull()!!
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

        "stages" -> {
            // Get stage history
            val stageHistoryResult = GetProjectStageHistoryStep.process(GetProjectStageHistoryInput(projectId))
            val stageChanges = when (stageHistoryResult) {
                is Result.Success -> stageHistoryResult.getOrNull()!!
                is Result.Failure -> emptyList()
            }

            ProjectTab.Stages(project, user, stageChanges)
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
                user,
                availableProjects,
                dependencyData.dependencies,
                dependencyData.dependents
            )
        }

        "location" -> ProjectTab.Location(project, user)

        "settings" -> {
            val worldMemberRole = worldMemberQueryStep.process(
                WorldMemberQueryInput(user.id, worldId)
            ).getOrNull()?.worldRole ?: Role.MEMBER
            ProjectTab.Settings(project, user, worldMemberRole)
        }

        else -> {
            val tasks = when (val tasksResult = SearchTasksStep.process(
                SearchTasksInput(projectId, userId = user.id)
            )) {
                is Result.Success -> tasksResult.value.tasks
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