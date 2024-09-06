package app.mcorg.presentation.templates.project

import app.mcorg.domain.*
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
            label {
                + "Done"
            }
            input {
                type = InputType.number
            }
            label {
                + "Needed"
            }
            input {
                type = InputType.number
            }
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
    ul {
        id = "task-list"
        script {
            src = "/static/scripts/progress-edit-dialog.js"
            nonce = generateNonce()
        }
        project.countable().forEach {
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
                    progress {
                        id = "project-task-${it.id}-progress"
                        max = it.needed.toString()
                        value = it.done.toString()
                    }
                    p {
                        + "Required: ${it.needed}"
                    }
                    p {
                        + "Done: ${it.done}"
                    }
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
                        onClick = "editClick()"
                        + "Edit"
                    }
                }
            }
        }
        project.doable().forEach {
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