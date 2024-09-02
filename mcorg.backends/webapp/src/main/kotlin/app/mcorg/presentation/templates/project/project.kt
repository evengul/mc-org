package app.mcorg.presentation.templates.project

import app.mcorg.domain.*
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun project(backLink: String, project: Project): String = subPageTemplate(project.name, backLink = backLink, listOf(
    NavBarRightIcon("", "Assign user", "/app/worlds/${project.worldId}/projects/${project.id}/assign"),
    NavBarRightIcon("", "Add task", "/app/worlds/${project.worldId}/projects/${project.id}/add-task")
)) {
    ul {
        project.countable().forEach {
            li {
                div {
                    + it.name
                    assignTask(project, it)
                }
                progress {
                    id = "project-task-${it.id}-progress"
                    max = it.needed.toString()
                    value = it.done.toString()
                }
            }
        }
        project.doable().forEach {
            li {
                div {
                    + it.name
                    assignTask(project, it)
                }
                input {
                    id = "project-doable-task-${it.id}-change-input"
                    type = InputType.checkBox
                    checked = it.isDone()
                }
            }
        }
    }
}

private fun DIV.assignTask(project: Project, task: Task) {
    a {
        id = "project-task-${task.id}-assign-link"
        href = "/app/worlds/${project.worldId}/projects/${project.id}/tasks/${task.id}/assign"
        button {
            id = "project-task-${task.id}-assign-button"
            if (task.assignee == null) {
                + "Assign user"
            } else {
                classes = setOf("selected")
                + task.assignee.username
            }
        }
    }
}