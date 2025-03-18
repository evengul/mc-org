package app.mcorg.presentation.templates.project

import app.mcorg.domain.model.projects.ProjectSpecification
import app.mcorg.domain.model.projects.SlimProject
import app.mcorg.domain.model.users.Profile
import app.mcorg.domain.model.users.User
import app.mcorg.presentation.templates.MainPage
import app.mcorg.presentation.templates.mainPageTemplate
import kotlinx.html.*

fun projects(
    worldId: Int,
    projects: List<SlimProject>,
    worldUsers: List<User>,
    currentUser: Profile,
    specification: ProjectSpecification,
    totalProjects: Int
): String = mainPageTemplate(
    selectedPage = MainPage.PROJECTS,
    worldId = worldId,
    title = "Projects",
) {
    addProjectDialog(worldId, currentUser.technicalPlayer)
    button {
        id = "show-create-project-dialog-button"
        onClick = "showDialog('add-project-dialog')"
        classes = setOf("button", "button-icon", "button-fab", "icon-menu-add")
    }
    form {
        id = "projects-filter"
        if (projects.isEmpty()) {
            style = "display: none;"
        }
        div {
            id = "project-filter-inputs"
            label {
                htmlFor = "project-search-input"
                +"Search"
            }
            input {
                value = specification.search ?: ""
                id = "projects-search-input"
                name = "search"
            }
            label {
                htmlFor = "project-hide-completed-checkbox"
                +"Hide done"
            }
            input {
                id = "project-hide-completed-checkbox"
                name = "hideCompleted"
                type = InputType.checkBox
                checked = specification.hideCompleted
            }
        }
        div {
            id = "project-filter-buttons"
            a {
                href = "/app/worlds/$worldId/projects"
                button {
                    classes = setOf("button-secondary")
                    type = ButtonType.button
                    +"Clear"
                }
            }
            button {
                type = ButtonType.submit
                id = ""
                +"Search"
            }
        }
    }
    if (projects.isEmpty()) {
        div {
            id = "empty-project-state"
            classes = setOf("empty-state")
            p {
                +"Welcome to your new world! Start by creating your first project."
            }
            button {
                onClick = "showDialog('add-project-dialog')"
                classes = setOf("button", "button-primary")
                +"Create Project"
            }
        }
    }
    p {
        id = "project-filter-amount"
        if (projects.size != totalProjects) {
            +"Showing ${projects.size} of $totalProjects projects"
        }
    }
    ul {
        id = "project-list"
        for (project in projects) {
            li {
                projectListElement(worldId, project, worldUsers, currentUser.toUser())
            }
        }
    }
}