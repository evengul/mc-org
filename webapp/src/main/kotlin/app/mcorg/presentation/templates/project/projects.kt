package app.mcorg.presentation.templates.project

import app.mcorg.domain.Profile
import app.mcorg.domain.SlimProject
import app.mcorg.domain.User
import app.mcorg.domain.toUser
import app.mcorg.presentation.entities.ProjectFiltersRequest
import app.mcorg.presentation.templates.MainPage
import app.mcorg.presentation.templates.mainPageTemplate
import app.mcorg.presentation.utils.allowedByFilter
import kotlinx.html.*

fun projects(worldId: Int, projects: List<SlimProject>, worldUsers: List<User>, currentUser: Profile, filtersRequest: ProjectFiltersRequest): String = mainPageTemplate(
    selectedPage = MainPage.PROJECTS,
    worldId = worldId,
    title = "Projects",
) {
    addProjectDialog(worldId, currentUser.technicalPlayer)
    if (projects.isNotEmpty())  {
        button {
            onClick = "showDialog('add-project-dialog')"
            classes = setOf("button", "button-icon", "button-fab", "icon-menu-add")
        }
        form {
            id = "projects-filter"
            div {
                id = "project-filter-inputs"
                label {
                    htmlFor = "project-search-input"
                    + "Search"
                }
                input {
                    value = filtersRequest.search ?: ""
                    id = "projects-search-input"
                    name = "search"
                }
                label {
                    htmlFor = "project-hide-completed-checkbox"
                    + "Hide done"
                }
                input {
                    id = "project-hide-completed-checkbox"
                    name = "hideCompleted"
                    type = InputType.checkBox
                    checked = filtersRequest.hideCompleted
                }
            }
            div {
                id = "project-filter-buttons"
                a {
                    href = "/app/worlds/$worldId/projects"
                    button {
                        classes = setOf("button-secondary")
                        type = ButtonType.button
                        + "Clear"
                    }
                }
                button {
                    type = ButtonType.submit
                    + "Search"
                }
            }
        }
    } else {
        div {
            classes = setOf("empty-state")
            p {
                + "Welcome to your new world! Start by creating your first project."
            }
            button {
                onClick = "showDialog('add-project-dialog')"
                classes = setOf("button", "button-primary")
                + "Create Project"
            }
        }
    }
    val filteredProjects = projects.filter { it.allowedByFilter(filtersRequest) }.sortedBy { it.name }
    p {
        id = "project-filter-amount"
        if (filteredProjects.size != projects.size) {
            + "Showing ${filteredProjects.size} of ${projects.size} projects"
        }
    }
    ul {
        id = "project-list"
        for (project in filteredProjects) {
            li {
                projectListElement(worldId, project, worldUsers, currentUser.toUser())
            }
        }
    }
}