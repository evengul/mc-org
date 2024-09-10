package app.mcorg.presentation.templates.project

import app.mcorg.domain.SlimProject
import app.mcorg.presentation.components.appProgress
import app.mcorg.presentation.hxDelete
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
                div {
                    classes = setOf("icon-row", "project-assignment")
                    span {
                        classes = setOf("icon icon-small icon-user-small")
                    }
                    a {
                        id = "projects-project-${project.id}-assign-link"
                        href = "/app/worlds/${project.worldId}/projects/${project.id}/assign?from=list"
                        p {
                            if (project.assignee == null) {
                                + "Assign user"
                            } else {
                                + project.assignee.username
                            }
                        }
                    }
                }
                button {
                    id = "projects-project-${project.id}-delete-button"
                    classes = setOf("button-danger project-delete")
                    hxDelete("/app/worlds/$worldId/projects/${project.id}")
                    + "Delete project"
                }
                appProgress(progressClasses = setOf("project-progress"), max = 1.0, value = project.progress)
            }
        }
    }
}