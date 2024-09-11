package app.mcorg.presentation.templates.task

import app.mcorg.domain.Project
import app.mcorg.domain.Task
import app.mcorg.domain.User
import app.mcorg.presentation.components.appProgress
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxTarget
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun createCountableTask(project: Project, task: Task, users: List<User>, currentUser: User) = createHTML().li {
    countableTask(project, task, users, currentUser)
}

fun LI.countableTask(project: Project, task: Task, users: List<User>, currentUser: User) {
    id = "task-${task.id}"
    classes = setOf("task")
    div {
        classes = setOf("task-name-assignment")
        + task.name
        select {
            assignTask(users, currentUser, project, task)
        }
    }
    div {
        classes = setOf("doable-task-progress")
        appProgress(max = task.needed.toDouble(), value = task.done.toDouble(), isItemAmount = true)
        button {
            disabled = task.done >= task.needed
            val toAdd = (task.needed - task.done + 64).coerceAtMost(64)
            hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${task.id}/do-more?done=$toAdd")
            hxTarget("#task-${task.id}")
            + "+1 stack"
        }
        button {
            disabled = task.done >= task.needed
            val toAdd = (task.needed - task.done + 64).coerceAtMost(1728)
            hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${task.id}/do-more?done=$toAdd")
            hxTarget("#task-${task.id}")
            + "+1 Shulker box"
        }
        button {
            disabled = task.done >= task.needed
            hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${task.id}/do-more?done=${task.needed - task.done}")
            hxTarget("#task-${task.id}")
            + "Done"
        }
        button {
            classes = setOf("button-danger")
            hxDelete("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${task.id}")
            hxTarget("#task-${task.id}")
            + "Delete"
        }
        button {
            id = "edit-task-${task.id}"
            classes = setOf("button-secondary")
            onClick = "editTask(this)"
            attributes["id"] = task.id.toString()
            attributes["needed"] = task.needed.toString()
            attributes["done"] = task.done.toString()
            + "Edit"
        }
    }
}