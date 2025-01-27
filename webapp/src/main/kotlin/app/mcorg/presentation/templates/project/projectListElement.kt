package app.mcorg.presentation.templates.project

import app.mcorg.domain.SlimProject
import app.mcorg.domain.User
import app.mcorg.presentation.components.appProgress
import app.mcorg.presentation.hxConfirm
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun createProjectListElement(worldId: Int, project: SlimProject, worldUsers: List<User>, currentUser: User) = createHTML().li {
    projectListElement(worldId, project, worldUsers, currentUser)
}

fun LI.projectListElement(worldId: Int, project: SlimProject, worldUsers: List<User>, currentUser: User) {
    id = "project-${project.id}"
    div {
        classes = setOf("project-header")
        a {
            href = "/app/worlds/$worldId/projects/${project.id}"
            h2 {
                + project.name
            }
        }
        button {
            classes = setOf("icon-row button button-icon icon-small icon-delete-small")
            hxDelete("/app/worlds/${project.worldId}/projects/${project.id}")
            hxTarget("#project-${project.id}")
            hxSwap("outerHTML")
            hxConfirm("Are you sure this project should be deleted? It can never be recovered, and all tasks will vanish.")
        }
    }

    div {
        classes = setOf("project-info")
        p {
            classes = setOf("icon-row")
            span { classes = setOf("icon", "icon-priority-${project.priority.name.lowercase()}") }
            + ("Priority: " + project.priority.name)
        }
        p {
            classes = setOf("icon-row")
            span { classes = setOf("icon", "icon-dimension-${project.dimension.name.lowercase()}") }
            + ("Dimension: " + project.dimension.name)
        }
    }

    div {
        classes = setOf("project-assignment")
        select {
            assignProject(project, worldUsers, User(currentUser.id, currentUser.username))
        }
    }
    appProgress(progressClasses = setOf("project-progress"), max = 1.0, value = project.progress)
}