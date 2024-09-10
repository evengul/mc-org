package app.mcorg.presentation.templates.task

import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun addTask(backLink: String, worldId: Int, projectId: Int) = subPageTemplate("Add task", backLink = backLink) {
    a {
        id = "add-task-doable-link"
        href = "/app/worlds/$worldId/projects/$projectId/add-task/doable"
        button {
            id = "add-task-doable-button"
            + "Add doable"
        }
    }
    a {
        id = "add-task-countable-link"
        href = "/app/worlds/$worldId/projects/$projectId/add-task/countable"
        button {
            id = "add-task-countable-button"
            + "Add countable"
        }
    }
    a {
        id = "add-task-litematica-link"
        href = "/app/worlds/$worldId/projects/$projectId/add-task/litematica"
        button {
            id = "add-task-litematica-button"
            + "Upload countable tasks from litematica requirements file"
        }
    }
}