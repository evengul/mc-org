package app.mcorg.presentation.templates.project

import app.mcorg.domain.*
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun project(backLink: String, project: Project): String = subPageTemplate(project.name, backLink = backLink, listOf(
    NavBarRightIcon("user", "Assign user", "/app/worlds/${project.worldId}/projects/${project.id}/assign"),
    NavBarRightIcon("menu-add", "Add task", "/app/worlds/${project.worldId}/projects/${project.id}/add-task")
)) {
    ul {
        id = "task-list"
        project.countable().forEach {
            li {
                classes = setOf("task")
                div {
                    classes = setOf("task-name-assign")
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
                classes = setOf("task")
                div {
                    classes = setOf("task-name-assign")
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
                classes = setOf("project-task-assign-button", "button-secondary", "icon-row")
                span {
                    classes = setOf("icon-small", "icon-user-small")
                }
                + "Assign user"
            } else {
                classes = setOf("project-task-assign-button", "selected", "button-secondary", "icon-row")
                span {
                    classes = setOf("icon-small", "icon-user-small")
                }
                + task.assignee.username
            }
        }
    }
}