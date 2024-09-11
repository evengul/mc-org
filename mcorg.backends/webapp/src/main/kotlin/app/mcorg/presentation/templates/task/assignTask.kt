package app.mcorg.presentation.templates.task

import app.mcorg.domain.Project
import app.mcorg.domain.Task
import app.mcorg.domain.User
import app.mcorg.domain.sortUsersBySelectedOrName
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import kotlinx.html.*

fun SELECT.assignTask(users: List<User>, currentUser: User, project: Project, task: Task) {
    val sortedUsers = sortUsersBySelectedOrName(users, currentUser, task.assignee)
    hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${task.id}/assign")
    hxTrigger("change changed")
    hxTarget("#task-${task.id}")
    hxSwap("outerHTML")
    name = "userId"
    option {
        selected = task.assignee == null
        value = "NONE"
        + "Unassigned"
    }
    sortedUsers.forEach {
        option {
            selected = task.assignee?.id == it.id
            value = it.id.toString()
            if (it.id == task.assignee?.id) {
                + "Assigned: ${it.username}"
            } else {
                + it.username
            }
        }
    }
}