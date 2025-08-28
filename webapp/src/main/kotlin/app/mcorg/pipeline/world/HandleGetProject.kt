package app.mcorg.pipeline.world

import app.mcorg.domain.model.project.toProjectResourceGathering
import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.presentation.mockdata.MockDependencies
import app.mcorg.presentation.mockdata.MockProjects
import app.mcorg.presentation.mockdata.MockResourceProduction
import app.mcorg.presentation.mockdata.MockStages
import app.mcorg.presentation.mockdata.MockTasks
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
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetProject() {
    val user = this.getUser()
    val tab = request.queryParameters["tab"]?.let {
        when (it) {
            "tasks", "resources", "location", "stages", "dependencies" -> it
            else -> null // Invalid tab, return null
        }
    }

    val project = MockProjects.getById(this.getProjectId()) ?: run {
        respondNotFound("Project not found")
        return
    }

    val unreadNotifications = getUnreadNotificationCount(user)

    val tasks = lazy {
        MockTasks.getTasksByProjectId(project.id)
    }

    val tabData = when (tab) {
        "resources" -> ProjectTab.Resources(
            project,
            tasks.value
                .flatMap { it.requirements }
                .filterIsInstance<ItemRequirement>()
                .toProjectResourceGathering(),
            MockResourceProduction.getByProjectId(project.id),
        )

        "location" -> ProjectTab.Location(project)
        "stages" -> ProjectTab.Stages(project, MockStages.getByProjectId(project.id))
        "dependencies" -> ProjectTab.Dependencies(
            project, MockDependencies.getDependenciesByProjectId(project.id),
            MockDependencies.getDependentsByProjectId(project.id)
        )

        else -> ProjectTab.Tasks(project, tasks.value)
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