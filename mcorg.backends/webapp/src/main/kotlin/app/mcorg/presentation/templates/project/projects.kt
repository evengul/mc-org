package app.mcorg.presentation.templates.project

import app.mcorg.domain.SlimProject
import app.mcorg.domain.User
import app.mcorg.domain.sortUsersBySelectedOrName
import app.mcorg.presentation.*
import app.mcorg.presentation.components.appProgress
import app.mcorg.presentation.templates.MainPage
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.mainPageTemplate
import io.ktor.util.*
import kotlinx.html.*

fun projects(worldId: Int, projects: List<SlimProject>, worldUsers: List<User>, currentUser: User): String = mainPageTemplate(
    selectedPage = MainPage.PROJECTS,
    worldId = worldId,
    title = "Projects",
    listOf(NavBarRightIcon("menu-add", "Add project", "/app/worlds/$worldId/projects/add"))
) {
    if (projects.isEmpty()) {
        p {
            + "No projects? "
            a {
                id = "projects-no-project-link"
                href = "/app/worlds/$worldId/projects/add"
                + "Add one now."
            }
        }
    }
    ul {
        id = "project-list"
        for (project in projects) {
            li {
                id = "project-${project.id}"
                div {
                    classes = setOf("icon-row", "project-info")
                    span {
                        title = "Priority: ${project.priority.name}"
                        classes = setOf("icon", "icon-priority-${project.priority.name.lowercase()}")
                    }
                    span {
                        title = "Dimension: ${project.dimension.name}"
                        classes = setOf("icon", "icon-dimension-${project.dimension.name.lowercase().replace("_", "-")}")
                    }
                    a {
                        href = "/app/worlds/$worldId/projects/${project.id}"
                        h2 {
                            + project.name
                        }
                    }
                }
                select {
                    assignProject(project, worldUsers, currentUser)
                }
                button {
                    id = "projects-project-${project.id}-delete-button"
                    classes = setOf("button-danger project-delete")
                    hxConfirm("Are you sure you want to delete this project? This can not be reverted, and all your tasks and progress will vanish.")
                    hxDelete("/app/worlds/$worldId/projects/${project.id}")
                    hxTarget("#project-${project.id}")
                    hxSwap("delete")
                    + "Delete project"
                }
                appProgress(progressClasses = setOf("project-progress"), max = 1.0, value = project.progress)
            }
        }
    }
}