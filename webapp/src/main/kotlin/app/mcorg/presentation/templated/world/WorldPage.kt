package app.mcorg.presentation.templated.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.user.WorldMember
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxIndicator
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.emptystate.EmptyStateVariant
import app.mcorg.presentation.templated.common.emptystate.emptyState
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.common.tabs.TabData
import app.mcorg.presentation.templated.common.tabs.tabsComponent
import app.mcorg.presentation.utils.BreadcrumbBuilder
import kotlinx.html.*
import kotlinx.html.ul

enum class WorldPageToggles {
    PROJECTS,
    KANBAN,
    ROADMAP,
    NEW_PROJECT,
    RESOURCE_MAP,
    SETTINGS,
    SEARCH,
}

fun worldPage(
    user: TokenProfile,
    world: World,
    worldMember: WorldMember,
    projects: List<Project>,
    supportedVersions: List<MinecraftVersion.Release>,
    tab: String? = null,
    unreadNotificationCount: Int = 0,
    toggles: Set<WorldPageToggles> = setOf(
        WorldPageToggles.PROJECTS,
        WorldPageToggles.NEW_PROJECT,
        WorldPageToggles.SEARCH,
        WorldPageToggles.SETTINGS,
    )
) = createPage(
    user = user,
    pageTitle = world.name,
    unreadNotificationCount = unreadNotificationCount,
    breadcrumbs = BreadcrumbBuilder.buildForWorld(world)
) {
    classes += "world"

    worldHeader(world, worldMember, supportedVersions, toggles)
    projectSearch(world.id, visibleProjects = projects.size, totalProjects = world.totalProjects, toggles)
    worldProjectsSection(world.totalProjects, projects, tab, toggles)
}

private fun MAIN.worldHeader(world: World, user: WorldMember, supportedVersions: List<MinecraftVersion.Release>, toggles: Set<WorldPageToggles>) {
    div("world-header") {
        worldHeaderInfo(world)
        worldHeaderActions(world, user, supportedVersions, toggles)
    }
}

private fun FlowContent.worldHeaderInfo(world: World) {
    div("world-header-start") {
        div("world-header-title") {
            h1 {
                + world.name
            }
            chipComponent {
                icon = Icons.Dimensions.OVERWORLD
                variant = ChipVariant.NEUTRAL
                + "MC ${world.version}"
            }
        }
        p("subtle") {
            + world.description
        }
    }
}

private fun FlowContent.worldHeaderActions(world: World, user: WorldMember, supportedVersions: List<MinecraftVersion.Release>, toggles: Set<WorldPageToggles>) {
    div("world-header-end") {
        if (WorldPageToggles.NEW_PROJECT in toggles) {
            createProjectModal(world.id, supportedVersions)
        }
        if (WorldPageToggles.RESOURCE_MAP in toggles) {
            neutralButton("Resource Map") {
                // TODO: Resource map ICON
                iconLeft = Icons.Menu.ROAD_MAP
                iconSize = IconSize.SMALL
            }
        }
        if (WorldPageToggles.SETTINGS in toggles && user.worldRole.isHigherThanOrEqualTo(Role.ADMIN)) {
            neutralButton("Settings") {
                // TODO(ICON): Settings icon
                iconLeft = Icons.Menu.UTILITIES
                iconSize = IconSize.SMALL
                href = Link.Worlds.world(world.id).settings().to
            }
        }
    }
}

private fun MAIN.projectSearch(worldId: Int, visibleProjects: Int, totalProjects: Int, toggles: Set<WorldPageToggles>) {
    if (WorldPageToggles.SEARCH in toggles) {
        div {
            form(classes = "world-projects-search") {
                hxGet(Link.Worlds.world(worldId).to + "/projects/search")
                hxTarget("#world-projects-list")
                hxSwap("outerHTML")
                hxTrigger("""
                input from:#world-projects-search-input delay:500ms, 
                change from:#world-projects-search-filter-completed-checkbox,
                change from:#world-projects-search-sort-select,
                submit
            """.trimIndent())
                hxIndicator(".search-wrapper")

                div("search-wrapper") {
                    input {
                        id = "world-projects-search-input"
                        type = InputType.search
                        placeholder = "Search projects by name, description, tasks..."
                        name = "query"
                    }
                }
                select {
                    id = "world-projects-search-sort-select"
                    name = "sortBy"
                    option {
                        selected = true
                        value = "modified_desc"
                        + "Sort by Last Modified"
                    }
                    option {
                        value = "name_asc"
                        + "Sort by Name (A-Z)"
                    }
                }
                input {
                    id = "world-projects-search-filter-completed-checkbox"
                    type = InputType.checkBox
                    name = "showCompleted"
                }
                label {
                    htmlFor = "world-projects-search-filter-completed-checkbox"
                    + "Show completed projects"
                }
            }
            p("subtle") {
                id = "world-projects-count"
                + "Showing $visibleProjects of $totalProjects project${if (totalProjects == 1) "" else "s"}"
            }
        }
    }
}

private fun MAIN.worldProjectsSection(
    totalProjects: Int,
    projects: List<Project>,
    tab: String?,
    toggles: Set<WorldPageToggles>
) {
    worldProjectsContent(totalProjects, projects, tab, toggles)
}

private fun DIV.worldProjectsEmpty() {
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
    totalProjects: Int,
    projects: List<Project>,
    tab: String?,
    toggles: Set<WorldPageToggles>
) {
    if (toggles.filter { it == WorldPageToggles.PROJECTS || it == WorldPageToggles.KANBAN || it == WorldPageToggles.ROADMAP }.size > 1) {
        tabsComponent(".world-project-content", TabData.create("Projects"), TabData.create("Kanban"), TabData.create("Roadmap")) {
            activeTab = tab ?: "projects"
        }
    }
    div("world-project-content") {
        worldProjectContent(totalProjects, tab, projects)
    }
}

fun DIV.worldProjectContent(totalProjects: Int, tab: String?, projects: List<Project>) {
    when(tab) {
        "kanban" -> kanbanView()
        "roadmap" -> roadmapView()
        else -> {
            if (totalProjects == 0) {
                worldProjectsEmpty()
            }
            ul {
                projectList(projects)
            }
        }
    }
}