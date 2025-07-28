package app.mcorg.presentation.templated.world

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.ChipColor
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.common.tabs.TabData
import app.mcorg.presentation.templated.common.tabs.tabsComponent
import kotlinx.html.InputType
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
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
    div("world-header") {
        div("world-header-start") {
            div("world-header-title") {
                h1 {
                    + world.name
                }
                chipComponent {
                    icon = Icons.Dimensions.OVERWORLD
                    color = ChipColor.NEUTRAL
                    + "MC ${world.version}"
                }
            }
            p("subtle") {
                + world.description
            }
        }
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
    if (projects.isEmpty()) {
        div("world-projects-empty") {
            h2 {
                + "No Active Projects"
            }
            p("subtle") {
                + "All your projects are completed or you haven't created any yet."
            }
        }
    } else {
        if (toggles.filter { it == WorldPageToggles.PROJECTS || it == WorldPageToggles.KANBAN || it == WorldPageToggles.ROADMAP }.size > 1) {
            tabsComponent(".world-project-content", TabData.create("Projects"), TabData.create("Kanban"), TabData.create("Roadmap")) {
                activeTab = tab ?: "projects"
            }
        }
        div("world-project-content") {
            when(tab) {
                "kanban" -> kanbanView()
                "roadmap" -> roadmapView()
                else -> ul {
                    projectList(projects)
                }
            }
        }
    }
}