package app.mcorg.presentation.templates.project

import app.mcorg.domain.SlimProject
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.mainPageTemplate
import kotlinx.html.*

fun projects(worldId: Int, projects: List<SlimProject>): String = mainPageTemplate(worldId, "Projects", listOf(
    NavBarRightIcon("Add project", "Add project", "/app/worlds/$worldId/projects/add")
)) {
    if (projects.isEmpty()) {
        + "No projects? "
        a {
            href = "/app/worlds/$worldId/projects/add"
            + "Add one now."
        }
    }
    ul {
        for (project in projects) {
            li {
                a {
                    href = "/app/worlds/${project.worldId}/projects/${project.id}"
                    div {
                        + (project.priority.name + " | " + project.dimension.name + " | " + project.name)
                    }
                    div {
                        a {
                            href = "/app/worlds/${project.worldId}/projects/${project.id}/assign"
                            if (project.assignee == null) {
                                + "Assign user"
                            } else {
                                + project.assignee.username
                            }
                        }
                    }
                    progress {
                        max = "100"
                        value = project.progress.toString()
                    }
                }
            }
        }
    }
}