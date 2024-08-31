package app.mcorg.presentation.templates.task

import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun addTask(backLink: String, worldId: Int, projectId: Int) = subPageTemplate("Add task", backLink = backLink) {
    a {
        href = "/app/worlds/$worldId/projects/$projectId/add-task/doable"
        button {
            + "Add doable"
        }
    }
    a {
        href = "/app/worlds/$worldId/projects/$projectId/add-task/countable"
        button {
            + "Add countable"
        }
    }
}