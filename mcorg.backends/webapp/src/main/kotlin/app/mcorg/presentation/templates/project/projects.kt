package app.mcorg.presentation.templates.project

import app.mcorg.domain.SlimProject
import app.mcorg.presentation.templates.MainPage
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.mainPageTemplate
import kotlinx.html.*

fun projects(worldId: Int, projects: List<SlimProject>): String = mainPageTemplate(
    selectedPage = MainPage.PROJECTS,
    worldId = worldId,
    title = "Projects",
    listOf(NavBarRightIcon("menu-add", "Add project", "/app/worlds/$worldId/projects/add"))
) {
    if (projects.isEmpty()) {
        + "No projects? "
        a {
            id = "projects-no-project-link"
            href = "/app/worlds/$worldId/projects/add"
            + "Add one now."
        }
    }
    ul {
        id = "project-list"
        for (project in projects) {
            li {
                div {
                    classes = setOf("project-info")
                    span {
                        classes = setOf("icon", "icon-dimension-${project.dimension.name.lowercase().replace("_", "-")}")
                    }
                    span {
                        classes = setOf("icon", "icon-priority-${project.priority.name.lowercase()}")
                    }
                    a {
                        href = "/app/worlds/$worldId/projects/${project.id}"
                        + project.name
                    }
                }
                div {
                    classes = setOf("project-assignment")
                    a {
                        id = "projects-project-${project.id}-assign-link"
                        href = "/app/worlds/${project.worldId}/projects/${project.id}/assign"
                        if (project.assignee == null) {
                            + "Assign user"
                        } else {
                            + project.assignee.username
                        }
                    }
                }
                progress {
                    classes = setOf("project-progress")
                    id = "projects-project-${project.id}-progress"
                    max = "100"
                    value = project.progress.toString()
                }
            }
        }
    }
}