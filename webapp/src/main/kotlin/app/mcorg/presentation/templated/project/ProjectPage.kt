package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.project.NamedProjectId
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectDependency
import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.domain.model.project.ProjectResourceGathering
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectStageChange
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.common.breadcrumb.Breadcrumbs
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.common.tabs.TabData
import app.mcorg.presentation.templated.common.tabs.tabsComponent
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.DIV
import kotlinx.html.SELECT
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select

sealed interface ProjectTab {
    val id: String
    val project: Project

    data class Tasks(override val project: Project, val totalTasksCount: Int, val tasks: List<Task>) : ProjectTab {
        override val id: String = "tasks"
    }

    data class Resources(
        override val project: Project,
        val resourceGathering: ProjectResourceGathering,
        val resourceProduction: List<ProjectProduction>,
        val itemNames: List<Item>
    ) : ProjectTab {
        override val id: String = "resources"
    }

    data class Location(override val project: Project) : ProjectTab {
        override val id: String = "location"
    }

    data class Stages(override val project: Project, val stageChanges: List<ProjectStageChange>) : ProjectTab {
        override val id: String = "stages"
    }

    data class Dependencies(override val project: Project, val availableProjects: List<NamedProjectId>, val dependencies: List<ProjectDependency>, val dependents: List<ProjectDependency>) : ProjectTab {
        override val id: String = "dependencies"
    }
    data class Settings(override val project: Project, val worldMemberRole: Role) : ProjectTab {
        override val id: String = "settings"
    }
}

fun projectPage(
    user: TokenProfile,
    data: ProjectTab,
    itemNames: List<Item>,
    unreadNotifications: Int,
    breadcrumbs: Breadcrumbs
) = createPage(
    user = user,
    pageTitle = data.project.name,
    unreadNotificationCount = unreadNotifications,
    breadcrumbs = breadcrumbs
) {
    val project = data.project
    classes += "project"
    div("project-header") {
        h1 {
            +project.name
        }
        div("project-header-content") {
            id = "project-header-content" // Target ID for HTMX replacement

            div("project-header-start") {
                chipComponent {
                    id = "project-stage-chip"
                    variant = ChipVariant.ACTION
                    +project.stage.toPrettyEnumName()
                }
                p("subtle") {
                    +"•"
                }
                p("subtle") {
                    id = "project-type"
                    +"${project.type.toPrettyEnumName()} Project"
                }
                project.location?.let {
                    p("subtle") {
                        +"•"
                    }
                    chipComponent {
                        classes += "project-location-chip"
                        variant = ChipVariant.NEUTRAL
                        text = "${it.x}, ${it.y}, ${it.z}"
                    }
                }
            }
            div("project-header-end") {
                select {
                    projectStageSelector(project.worldId, project.id, project.stage)
                }
                createTaskModal(project, itemNames, CreateTaskModalTab.ITEM_REQUIREMENT)
            }
        }
        p("subtle") {
            id = "project-description"
            + project.description
        }
    }
    div("project-content") {
        tabsComponent(
            hxTarget = ".project-tabs-content",
            TabData.create("Tasks"),
            TabData.create("Resources"),
            TabData.create("Location"),
            TabData.create("Stages"),
            TabData.create("Dependencies"),
            TabData.create("Settings")
        ) {
            activeTab = data.id
        }
        div {
            projectTabsContent(data)
        }
    }
}

fun SELECT.projectStageSelector(worldId: Int, projectId: Int, selectedStage: ProjectStage) {
    id = "project-stage-selector"
    name = "stage"

    // HTMX attributes for dynamic stage updates
    hxPatch(Link.Worlds.world(worldId).project(projectId).to + "/stage")
    hxTarget("#project-stage-selector")
    hxSwap("outerHTML")
    hxTrigger("change changed")

    ProjectStage.entries.forEach {
        option {
            value = it.name
            if (it == selectedStage) {
                selected = true
            }
            +it.toPrettyEnumName()
        }
    }
}

fun DIV.projectTabsContent(data: ProjectTab) {
    classes += "project-tabs-content"
    when(data) {
        is ProjectTab.Tasks -> tasksTab(data.project, data.totalTasksCount, data.tasks)
        is ProjectTab.Resources -> resourcesTab(data.project, data.resourceProduction, data.resourceGathering, data.itemNames)
        is ProjectTab.Location -> locationTab(data.project)
        is ProjectTab.Stages -> stagesTab(data.stageChanges)
        is ProjectTab.Dependencies -> dependenciesTab(data.project.worldId, data.project.id, data.availableProjects, data.dependencies, data.dependents)
        is ProjectTab.Settings -> projectSettingsTab(data.project, data.worldMemberRole)
    }
}