package app.mcorg.presentation.templates.task.board

import app.mcorg.domain.projects.*
import app.mcorg.domain.users.User
import app.mcorg.presentation.hxConfirm
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templates.task.assignTask
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import kotlin.math.floor
import kotlin.math.roundToInt

fun createTaskBoard(project: Project, users: List<User>, currentUser: User) = createHTML().main {
    taskBoard(project, users, currentUser)
}

fun MAIN.taskBoard(project: Project, users: List<User>, currentUser: User) {
    fun DIV.internalColumn(columnId: String, tasks: List<Task>, stage: TaskStage, actionButton: (BUTTON.() -> Unit)? = null) = taskColumn(
        columnId,
        project.worldId,
        project.id,
        tasks,
        stage,
        users,
        currentUser,
        actionButton
    )
    div {
        id = "task-board"
        section {
            id = "doable-tasks"
            h2 {
                + "Doable Tasks"
                button {
                    classes = setOf("toggle-button")
                    onClick = "toggleVisibility('doable-tasks-columns', this)"
                    i {
                        classes = setOf("caret")
                    }
                }
            }
            div {
                id = "doable-tasks-columns"
                classes = setOf("task-columns")
                internalColumn("task-list-doable-todo", project.doable().filter { it.stage == TaskStages.TODO }, TaskStages.TODO) {
                    id = "add-doable-task-button"
                    onClick = "showDialog('add-task-doable-dialog', this)"
                    classes = setOf("button", "button-icon", "icon-menu-add")
                }
                internalColumn("task-list-doable-in-progress", project.doable().filter { it.stage == TaskStages.IN_PROGRESS }, TaskStages.IN_PROGRESS)
                internalColumn("task-list-doable-done", project.doable().filter { it.stage == TaskStages.DONE }, TaskStages.DONE)
            }
        }
        section {
            id = "countable-tasks"
            h2 {
                + "Countable Tasks"
                button {
                    id = "add-litematica-tasks-button"
                    classes = setOf("button", "button-icon", "icon-menu-add")
                    onClick = "showDialog('add-task-litematica-dialog')"
                }
                button {
                    classes = setOf("toggle-button")
                    onClick = "toggleVisibility('countable-tasks-columns', this)"
                    i {
                        classes = setOf("caret")
                    }
                }
            }
            div {
                id = "countable-tasks-columns"
                classes = setOf("task-columns")
                internalColumn("task-list-countable-todo", project.countable().filter { it.stage == TaskStages.TODO }, TaskStages.TODO) {
                    id = "add-countable-task-button"
                    classes = setOf("button", "button-icon", "icon-menu-add")
                    onClick = "showDialog('add-task-countable-dialog')"
                }
                internalColumn("task-list-countable-in-progress", project.countable().filter { it.stage == TaskStages.IN_PROGRESS }, TaskStages.IN_PROGRESS)
                internalColumn("task-list-countable-done", project.countable().filter { it.stage == TaskStages.DONE }, TaskStages.DONE)
            }
        }
    }
}

private fun DIV.taskColumn(columnId: String,
                           worldId: Int,
                           projectId: Int,
                           tasks: List<Task>,
                           stage: TaskStage,
                           users: List<User>,
                           currentUser: User,
                           actionButton: (BUTTON.() -> Unit)? = null) {
    div {
        id = stage.id
        classes = setOf("task-column")
        h3 {
            + stage.name
        }
        if (actionButton != null) {
            button {
                actionButton()
            }
        }
        ul {
            id = columnId
            tasks.forEach {
                li {
                    id = "task-${it.id}"
                    classes = mutableSetOf("task").apply {
                        if (stage == TaskStages.DONE) add("task-done")
                    }
                    attributes["draggable"] = "true"
                    attributes["ondragstart"] = "onDragStart(event)"
                    attributes["ondragover"] = "onDragOver(event)"
                    attributes["ondrop"] = "onDrop(event)"
                    button {
                        classes = setOf("button", "button-icon", "icon-delete", "delete-task-button")
                        hxDelete("/app/worlds/${worldId}/projects/${projectId}/tasks/${it.id}")
                        hxConfirm("Are you sure you want to delete this task?")
                        hxTarget("#task-${it.id}")
                    }
                    div {
                        classes = setOf("task-content")
                        h3 {
                            + it.name
                            if (it.priority != Priority.NONE) {
                                i {
                                    classes = setOf("task-priority", "icon", when (it.priority) {
                                        Priority.LOW -> "icon icon-priority-low"
                                        Priority.MEDIUM -> "icon icon-priority-medium"
                                        Priority.HIGH -> "icon icon-priority-high"
                                        else -> ""
                                    })
                                }
                            }

                        }
                        span {
                            classes = setOf("task-assignee")
                            select {
                                assignTask(
                                    worldId,
                                    projectId,
                                    it,
                                    users,
                                    currentUser
                                )
                            }
                        }
                        if (it.isCountable()) {
                            span {
                                classes = setOf("task-add-buttons")
                                button {
                                    title = "Add 1 stack = 64 items"
                                    disabled = it.done >= it.needed
                                    classes = setOf("task-add-button")
                                    val toAdd = (it.needed - it.done + 64).coerceAtMost(64)
                                    hxPatch("/app/worlds/${worldId}/projects/${projectId}/tasks/${it.id}/do-more?done=$toAdd")
                                    hxTarget("#task-${it.id}")
                                    + "+Stack"
                                }
                                button {
                                    title = "Add 1 shulker box = 1728 items"
                                    disabled = it.done >= it.needed
                                    classes = setOf("task-add-button")
                                    val toAdd = (it.needed - it.done + 64).coerceAtMost(1728)
                                    hxPatch("/app/worlds/${worldId}/projects/${projectId}/tasks/${it.id}/do-more?done=$toAdd")
                                    hxTarget("#task-${it.id}")
                                    + "+SB"
                                }
                                button {
                                    classes = setOf("button-secondary task-add-button")
                                    id = "edit-task-${it.id}"
                                    onClick = "editTask(this)"
                                    attributes["id"] = it.id.toString()
                                    attributes["needed"] = it.needed.toString()
                                    attributes["done"] = it.done.toString()
                                    + "Update"
                                }
                            }
                            p {
                                classes = setOf("task-progress")
                                + ("${getItemProgress(it.done.toDouble())} of ${getItemProgress(it.needed.toDouble())}")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getItemProgress(amount: Double): String {
    val shulkers = floor(amount / 1728.0).roundToInt()
    val stacks = floor((amount - shulkers * 1728.0) / 64.0).roundToInt()
    val items = floor(amount - shulkers * 1728 - stacks * 64.0).roundToInt()

    var content = ""
    if (shulkers > 0) {
        content += "$shulkers shulker boxes"
        if (stacks > 0 || amount > 0) {
            content += ", "
        }
    }
    if (stacks > 0) {
        content += "$stacks stacks"
        if (amount > 0) {
            content += ", "
        }
    }
    if (items > 0) {
        content += "$items items"
    }
    return content.takeIf { it.isNotEmpty() } ?: "0 items"
}