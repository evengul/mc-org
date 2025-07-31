package app.mcorg.presentation.templated.world

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.common.tabs.TabData
import app.mcorg.presentation.templated.common.tabs.tabsComponent
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
    projects: List<Project>,
    tab: String? = null,
    toggles: Set<WorldPageToggles> = setOf(
        WorldPageToggles.PROJECTS,
        WorldPageToggles.NEW_PROJECT,
        WorldPageToggles.SETTINGS,
    )
) = createPage(
    user = user,
    pageTitle = world.name
) {
    classes += "world"

    worldHeader(world, toggles)
    worldSearchSection(toggles)
    worldProjectsSection(projects, tab, toggles)
}

private fun MAIN.worldHeader(world: World, toggles: Set<WorldPageToggles>) {
    div("world-header") {
        worldHeaderInfo(world)
        worldHeaderActions(world, toggles)
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

private fun FlowContent.worldHeaderActions(world: World, toggles: Set<WorldPageToggles>) {
    div("world-header-end") {
        if (WorldPageToggles.NEW_PROJECT in toggles) {
            createProjectModal(world.id)
        }
        if (WorldPageToggles.RESOURCE_MAP in toggles) {
            neutralButton("Resource Map") {
                // TODO: Resource map ICON
                iconLeft = Icons.Menu.ROAD_MAP
                iconSize = IconSize.SMALL
            }
        }
        if (WorldPageToggles.SETTINGS in toggles) {
            neutralButton("Settings") {
                // TODO(ICON): Settings icon
                iconLeft = Icons.Menu.UTILITIES
                iconSize = IconSize.SMALL
                href = Link.Worlds.world(world.id).settings().to
            }
        }
    }
}

private fun MAIN.worldSearchSection(toggles: Set<WorldPageToggles>) {
    if (WorldPageToggles.SEARCH in toggles) {
        div("world-projects-search") {
            input {
                placeholder = "Search projects by name, description, tasks..."
            }
            input {
                id = "world-projects-search-filter-completed-checkbox"
                type = InputType.checkBox
            }
            label {
                htmlFor = "world-projects-search-filter-completed-checkbox"
                + "Show completed projects"
            }
        }
    }
}

private fun MAIN.worldProjectsSection(
    projects: List<Project>,
    tab: String?,
    toggles: Set<WorldPageToggles>
) {
    worldProjectsContent(projects, tab, toggles)
}

private fun DIV.worldProjectsEmpty() {
    div("world-projects-empty") {
        h2 {
            + "No Active Projects"
        }
        p("subtle") {
            + "All your projects are completed or you haven't created any yet."
        }
    }
}

private fun MAIN.worldProjectsContent(
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
        worldProjectContent(tab, projects)
    }
}

fun DIV.worldProjectContent(tab: String?, projects: List<Project>) {
    when(tab) {
        "kanban" -> kanbanView()
        "roadmap" -> roadmapView()
        else -> if (projects.isEmpty()) {
            worldProjectsEmpty()
        } else {
            ul("world-projects-list") {
                projectList(projects)
            }
        }
    }
}