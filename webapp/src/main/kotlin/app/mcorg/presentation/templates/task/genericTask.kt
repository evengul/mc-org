package app.mcorg.presentation.templates.task

import app.mcorg.domain.projects.Project
import app.mcorg.domain.projects.Task
import app.mcorg.domain.users.User
import app.mcorg.presentation.hxConfirm
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.*

fun LI.genericTask(project: Project, task: Task, users: List<User>, currentUser: User) {
    id = "task-${task.id}"
    classes = setOf("task")
    span {
        classes = setOf("task-name-delete")
        h2 {
            + task.name
        }
        button {
            classes = setOf("icon-row button button-icon icon-small icon-delete-small")
            hxDelete("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${task.id}")
            hxTarget("#task-${task.id}")
            hxSwap("outerHTML")
            hxConfirm("Are you sure this task should be deleted?")
        }
    }
    select {
        assignTask(users, currentUser, project, task)
    }
}