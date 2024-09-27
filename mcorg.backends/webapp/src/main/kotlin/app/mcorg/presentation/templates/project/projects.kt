package app.mcorg.presentation.templates.project

import app.mcorg.domain.Profile
import app.mcorg.domain.SlimProject
import app.mcorg.domain.User
import app.mcorg.domain.toUser
import app.mcorg.presentation.entities.ProjectFiltersRequest
import app.mcorg.presentation.templates.MainPage
import app.mcorg.presentation.templates.mainPageTemplate
import kotlinx.html.*

fun projects(worldId: Int, projects: List<SlimProject>, worldUsers: List<User>, currentUser: Profile, filtersRequest: ProjectFiltersRequest): String = mainPageTemplate(
    selectedPage = MainPage.PROJECTS,
    worldId = worldId,
    title = "Projects",
) {
    addProjectDialog(worldId, currentUser.technicalPlayer)
    button {
        onClick = "showDialog('add-project-dialog')"
        classes = setOf("button", "button-icon", "button-fab", "icon-menu-add")
    }
    if (projects.isNotEmpty())  {
        details {
            summary {
                + "Search"
            }
            form {
                id = "projects-filter"
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

    }
    val filteredProjects = projects.filter { keepProject(it, filtersRequest) }.sortedBy { it.name }
    if (filteredProjects.size != projects.size) {
        p {
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

fun keepProject(project: SlimProject, filtersRequest: ProjectFiltersRequest): Boolean {
    if (filtersRequest.hideCompleted && project.progress >= 1.0) {
        return false
    }

    if (!filtersRequest.search.isNullOrBlank()) {
        val search = filtersRequest.search.lowercase()
        val username = project.assignee?.username?.lowercase()
        val name = project.name.lowercase()
        if (username == null) {
            if (!name.contains(search)) {
                return false
            }
        } else {
            if (!username.contains(search) && !name.contains(search)) {
                return false
            }
        }
    }

    return true
}