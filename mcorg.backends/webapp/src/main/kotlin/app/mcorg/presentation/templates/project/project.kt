package app.mcorg.presentation.templates.project

import app.mcorg.domain.*
import app.mcorg.presentation.components.appProgress
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.subPageTemplate
import io.ktor.util.*
import kotlinx.html.*

fun project(backLink: String, project: Project): String = subPageTemplate(project.name, backLink = backLink, listOf(
    NavBarRightIcon("user", "Assign user", "/app/worlds/${project.worldId}/projects/${project.id}/assign?from=single"),
    NavBarRightIcon("menu-add", "Add task", "/app/worlds/${project.worldId}/projects/${project.id}/add-task")
)) {
    dialog {
        id = "edit-task-dialog"
        h1 {
            + "Edit task"
        }
        form {
            hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/requirements")
            input {
                id = "edit-task-id-input"
                name = "id"
                type = InputType.text
            }
            label {
                htmlFor = "edit-task-done-input"
                + "Done"
            }
            input {
                name = "done"
                id = "edit-task-done-input"
                type = InputType.number
                required = true
                min = "0"
            }
            label {
                htmlFor = "edit-task-needed-input"
                + "Needed"
            }
            input {
                name = "needed"
                id = "edit-task-needed-input"
                type = InputType.number
                required = true
                min = "0"
                max = "200000000"
            }
            span {
                classes = setOf("button-row")
                button {
                    onClick = "cancelDialog()"
                    classes = setOf("button-secondary")
                    type = ButtonType.button
                    + "Cancel"
                }
                button {
                    type = ButtonType.submit
                    + "Save"
                }
            }
        }
    }
    ul {
        id = "task-list"
        script {
            src = "/static/scripts/progress-edit-dialog.js"
            nonce = generateNonce()
        }
        project.countable().sortedBy { it.id }.forEach {
            li {
                classes = setOf("task")
                div {
                    classes = setOf("task-name-assign")
                    h2 {
                        + it.name
                    }
                    assignTask(project, it)
                }
                div {
                    classes = setOf("doable-task-progress")
                    appProgress(max = it.needed.toDouble(), value = it.done.toDouble(), isItemAmount = true)
                    button {
                        disabled = it.done + 1 > it.needed
                        hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}/do-more?done=1")
                        + "+1"
                    }
                    button {
                        disabled = it.done + 64 > it.needed
                        hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}/do-more?done=64")
                        + "+1 stack"
                    }
                    button {
                        disabled = it.done + 1728 > it.needed
                        hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}/do-more?done=1728")
                        + "+1 Shulker box"
                    }
                    button {
                        disabled = it.done >= it.needed
                        hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}/do-more?done=${it.needed - it.done}")
                        + "Done"
                    }
                    button {
                        id = "edit-task-${it.id}"
                        classes = setOf("button-secondary")
                        onClick = "editTask(this)"
                        attributes["id"] = it.id.toString()
                        attributes["needed"] = it.needed.toString()
                        attributes["done"] = it.done.toString()
                        + "Edit"
                    }
                }
            }
        }
        project.doable().sortedBy { it.id }.forEach {
            li {
                classes = setOf("task")
                div {
                    classes = setOf("task-name-assign")
                    h2 {
                        + it.name
                    }
                    assignTask(project, it)
                }
                input {
                    id = "project-doable-task-${it.id}-change-input"
                    if (it.isDone()) {
                        hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}/incomplete")
                    } else {
                        hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}/complete")
                    }
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
                p {
                    + "Assign user"
                }
            } else {
                classes = setOf("project-task-assign-button", "selected", "button-secondary", "icon-row")
                span {
                    classes = setOf("icon-small", "icon-user-small")
                }
                p {
                    + task.assignee.username
                }
            }
        }
    }
}