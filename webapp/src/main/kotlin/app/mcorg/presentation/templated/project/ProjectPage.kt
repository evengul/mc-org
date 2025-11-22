package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.project.*
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.breadcrumb.Breadcrumbs
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.page.PageScript
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.common.tabs.TabData
import app.mcorg.presentation.templated.common.tabs.tabsComponent
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

sealed interface ProjectTab {
    val id: String
    val user: TokenProfile
    val project: Project

    data class Tasks(
        override val project: Project,
        override val user: TokenProfile,
        val totalTasksCount: Int,
        val tasks: List<Task>
    ) : ProjectTab {
        override val id: String = "tasks"
    }

    data class Resources(
        override val project: Project,
        override val user: TokenProfile,
        val resourceGathering: ProjectResourceGathering,
        val resourceProduction: List<ProjectProduction>,
        val itemNames: List<Item>
    ) : ProjectTab {
        override val id: String = "resources"
    }

    data class Location(override val project: Project, override val user: TokenProfile) : ProjectTab {
        override val id: String = "location"
    }

    data class Dependencies(
        override val project: Project,
        override val user: TokenProfile,
        val availableProjects: List<NamedProjectId>,
        val dependencies: List<ProjectDependency>,
        val dependents: List<ProjectDependency>
    ) : ProjectTab {
        override val id: String = "dependencies"
    }

    data class Settings(override val project: Project, override val user: TokenProfile, val worldMemberRole: Role) :
        ProjectTab {
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
    pageScripts = setOf(PageScript.SEARCHABLE_SELECT),
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
                    hxEditableFromHref =
                        Link.Worlds.world(project.worldId).project(project.id).to + "/stage-select-fragment"
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
                project.importedFromIdea?.let {
                    p("subtle") {
                        + "•"
                    }
                    p("subtle") {
                        + "Imported from Idea: "
                        a {
                            href = Link.Ideas.single(it.first)
                            + it.second
                        }
                    }

                }
            }
            div("project-header-end") {
                createTaskModal(user, project, itemNames, CreateTaskModalTab.ITEM_REQUIREMENT)
            }
        }
        p("subtle") {
            id = "project-description"
            +project.description
        }
    }
    div("project-content") {
        tabsComponent(
            hxTarget = ".project-tabs-content",
            TabData.create("Tasks"),
            TabData.create("Resources"),
            TabData.create("Location"),
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

fun DIV.projectTabsContent(tab: ProjectTab) {
    classes += "project-tabs-content"
    when (tab) {
        is ProjectTab.Tasks -> tasksTab(tab)
        is ProjectTab.Resources -> resourcesTab(
            tab.user,
            tab.project,
            tab.resourceProduction,
            tab.resourceGathering,
            tab.itemNames
        )

        is ProjectTab.Location -> locationTab(tab.user, tab.project)
        is ProjectTab.Dependencies -> dependenciesTab(
            tab.user,
            tab.project.worldId,
            tab.project.id,
            tab.availableProjects,
            tab.dependencies,
            tab.dependents
        )

        is ProjectTab.Settings -> projectSettingsTab(tab.user, tab.project, tab.worldMemberRole)
    }
}