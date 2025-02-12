package app.mcorg.presentation.templates.task.board

import app.mcorg.domain.projects.*
import app.mcorg.domain.users.User
import app.mcorg.presentation.components.appProgress
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templates.task.assignTask
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun createTaskBoard(project: Project, users: List<User>, currentUser: User) = createHTML().main {
    taskBoard(project, users, currentUser)
}

fun MAIN.taskBoard(project: Project, users: List<User>, currentUser: User) {
    fun DIV.internalColumn(tasks: List<Task>, stage: TaskStage) = taskColumn(
        project.worldId,
        project.id,
        tasks,
        stage,
        users,
        currentUser
    )
    div {
        id = "task-board"
        section {
            id = "doable-tasks"
            h2 {
                + "Doable Tasks"
            }
            div {
                classes = setOf("task-columns")
                internalColumn(project.doable().filter { it.stage == TaskStages.TODO }, TaskStages.TODO)
                internalColumn(project.doable().filter { it.stage == TaskStages.IN_PROGRESS }, TaskStages.IN_PROGRESS)
                internalColumn(project.doable().filter { it.stage == TaskStages.DONE }, TaskStages.DONE)
            }
        }
        section {
            id = "countable-tasks"
            h2 {
                + "Countable Tasks"
            }
            div {
                classes = setOf("task-columns")
                internalColumn(project.countable().filter { it.stage == TaskStages.TODO }, TaskStages.TODO)
                internalColumn(project.countable().filter { it.stage == TaskStages.IN_PROGRESS }, TaskStages.IN_PROGRESS)
                internalColumn(project.countable().filter { it.stage == TaskStages.DONE }, TaskStages.DONE)
            }
        }
    }
}

private fun DIV.taskColumn(worldId: Int, projectId: Int, tasks: List<Task>, stage: TaskStage, users: List<User>, currentUser: User) {
    div {
        id = stage.id
        classes = setOf("task-column")
        h3 {
            + stage.name
        }
        ul {
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
                    i {
                        classes = setOf("task-priority", "icon", when (it.priority) {
                            Priority.NONE -> "icon icon-priority-low"
                            Priority.LOW -> "icon icon-priority-low"
                            Priority.MEDIUM -> "icon icon-priority-medium"
                            Priority.HIGH -> "icon icon-priority-high"
                        })
                    }
                    div {
                        classes = setOf("task-content")
                        h3 {
                            + it.name
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
                            appProgress(
                                progressClasses = setOf("task-progress"),
                                value = it.done.toDouble(),
                                max = it.needed.toDouble(),
                                isItemAmount = true
                            )
                        }
                    }
                }
            }
        }
    }
}