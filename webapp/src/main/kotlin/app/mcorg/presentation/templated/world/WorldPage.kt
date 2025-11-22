package app.mcorg.presentation.templated.world

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.user.WorldMember
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.*
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.emptystate.EmptyStateVariant
import app.mcorg.presentation.templated.common.emptystate.emptyState
import app.mcorg.presentation.templated.common.form.searchField.searchField
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.common.tabs.TabData
import app.mcorg.presentation.templated.common.tabs.tabsComponent
import app.mcorg.presentation.utils.BreadcrumbBuilder
import kotlinx.html.*

enum class WorldPageToggles {
    PROJECTS,
    KANBAN,
    ROADMAP,
    NEW_PROJECT,
    RESOURCE_MAP,
    SETTINGS,
    SEARCH,
}

sealed interface WorldPageTabData {
    val id: String
    val world: World
    val worldMember: WorldMember

    data class ProjectsData(
        override val world: World,
        override val worldMember: WorldMember,
        val projects: List<Project>
    ) : WorldPageTabData {
        override val id: String = "projects"
    }

    data class KanbanData(
        override val world: World,
        override val worldMember: WorldMember
    ) : WorldPageTabData {
        override val id: String = "kanban"
    }

    data class RoadmapData(
        override val world: World,
        override val worldMember: WorldMember,
        val roadmap: Roadmap
    ) : WorldPageTabData {
        override val id: String = "roadmap"
    }
}

fun worldPage(
    user: TokenProfile,
    tabData: WorldPageTabData,
    unreadNotificationCount: Int = 0,
    toggles: Set<WorldPageToggles> = setOf(
        WorldPageToggles.PROJECTS,
        WorldPageToggles.ROADMAP,
        WorldPageToggles.NEW_PROJECT,
        WorldPageToggles.SEARCH,
        WorldPageToggles.SETTINGS,
    )
) = createPage(
    user = user,
    pageTitle = tabData.world.name,
    unreadNotificationCount = unreadNotificationCount,
    breadcrumbs = BreadcrumbBuilder.buildForWorld(tabData.world)
) {
    classes += "world"

    worldHeader(user, tabData.world, tabData.worldMember, toggles)
    worldProjectsSection(tabData, toggles)
}

private fun MAIN.worldHeader(
    user: TokenProfile,
    world: World,
    worldMember: WorldMember,
    toggles: Set<WorldPageToggles>
) {
    div("world-header") {
        worldHeaderInfo(world)
        worldHeaderActions(user, world, worldMember, toggles)
    }
}

private fun FlowContent.worldHeaderInfo(world: World) {
    div("world-header-start") {
        div("world-header-title") {
            h1 {
                +world.name
            }
            chipComponent {
                icon = Icons.Dimensions.OVERWORLD
                variant = ChipVariant.NEUTRAL
                +"MC ${world.version}"
            }
        }
        p("subtle") {
            +world.description
        }
    }
}

private fun FlowContent.worldHeaderActions(
    user: TokenProfile,
    world: World,
    worldMember: WorldMember,
    toggles: Set<WorldPageToggles>
) {
    div("world-header-end") {
        if (WorldPageToggles.NEW_PROJECT in toggles) {
            createProjectModal(user, world.id)
        }
        if (WorldPageToggles.RESOURCE_MAP in toggles) {
            neutralButton("Resource Map") {
                // TODO: Resource map ICON
                iconLeft = Icons.Menu.ROAD_MAP
                iconSize = IconSize.SMALL
            }
        }
        if (WorldPageToggles.SETTINGS in toggles && worldMember.worldRole.isHigherThanOrEqualTo(Role.ADMIN) && !user.isDemoUserInProduction) {
            neutralButton("Settings") {
                // TODO(ICON): Settings icon
                iconLeft = Icons.Menu.UTILITIES
                iconSize = IconSize.SMALL
                href = Link.Worlds.world(world.id).settings().to
            }
        }
    }
}

private fun DIV.projectSearch(worldId: Int, visibleProjects: Int, totalProjects: Int) {
    div {
        form(classes = "world-projects-search") {
            hxGet(Link.Worlds.world(worldId).to + "/projects/search")
            hxTarget("#world-projects-list")
            hxSwap("outerHTML")
            hxTrigger(
                """
                input from:#world-projects-search-input delay:500ms, 
                change from:#world-projects-search-input changed, 
                change from:#world-projects-search-filter-completed-checkbox,
                change from:#world-projects-search-sort-select,
                submit
            """.trimIndent()
            )
            hxIndicator(".search-wrapper")

            searchField("world-projects-search-input") {
                placeHolder = "Search projects by name or description..."
            }
            select {
                id = "world-projects-search-sort-select"
                name = "sortBy"
                option {
                    selected = true
                    value = "modified_desc"
                    +"Sort by Last Modified"
                }
                option {
                    value = "name_asc"
                    +"Sort by Name (A-Z)"
                }
            }
            input {
                id = "world-projects-search-filter-completed-checkbox"
                type = InputType.checkBox
                name = "showCompleted"
            }
            label {
                htmlFor = "world-projects-search-filter-completed-checkbox"
                +"Show completed projects"
            }
        }
        p("subtle") {
            id = "world-projects-count"
            +"Showing $visibleProjects of $totalProjects project${if (totalProjects == 1) "" else "s"}"
        }
    }
}

private fun MAIN.worldProjectsSection(
    tabData: WorldPageTabData,
    toggles: Set<WorldPageToggles>
) {
    worldProjectsContent(tabData, toggles)
}

fun DIV.worldProjectsEmpty() {
    emptyState(
        id = "empty-projects-state",
        title = "No Active Projects",
        description = "All your projects are completed or you haven't created any yet. Start a new project to organize your builds.",
        icon = Icons.Menu.PROJECTS,
        variant = EmptyStateVariant.INLINE
    ) {
        actionButton("Create your first project") {
            onClick = "document.getElementById('create-project-modal')?.showModal()"
        }
    }
}

private fun MAIN.worldProjectsContent(
    tabData: WorldPageTabData,
    toggles: Set<WorldPageToggles>
) {
    if (toggles.filter { it == WorldPageToggles.PROJECTS || it == WorldPageToggles.KANBAN || it == WorldPageToggles.ROADMAP }.size > 1) {
        tabsComponent(mutableListOf<TabData>().apply {
            if (WorldPageToggles.PROJECTS in toggles) add(TabData.create("projects", "Projects"))
            if (WorldPageToggles.KANBAN in toggles) add(TabData.create("kanban", "Kanban"))
            if (WorldPageToggles.ROADMAP in toggles) add(TabData.create("roadmap", "Roadmap"))
        }) {
            hxTarget = ".world-project-content"
            activeTab = tabData.id
        }
    }
    div("world-project-content") {
        worldProjectContent(tabData)
    }
}

fun DIV.worldProjectContent(tabData: WorldPageTabData) {
    when (tabData) {
        is WorldPageTabData.KanbanData -> kanbanView()
        is WorldPageTabData.RoadmapData -> roadmapView(tabData)
        is WorldPageTabData.ProjectsData -> {
            if (tabData.world.totalProjects == 0) {
                worldProjectsEmpty()
            } else {
                projectSearch(
                    tabData.world.id,
                    visibleProjects = tabData.projects.size,
                    totalProjects = tabData.world.totalProjects
                )
            }
            ul {
                projectList(tabData.projects)
            }
        }
    }
}