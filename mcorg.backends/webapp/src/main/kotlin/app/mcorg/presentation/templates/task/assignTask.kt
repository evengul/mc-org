package app.mcorg.presentation.templates.task

import app.mcorg.domain.Project
import app.mcorg.domain.Task
import app.mcorg.domain.User
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templates.assign
import kotlinx.html.*

fun SELECT.assignTask(users: List<User>, currentUser: User, project: Project, task: Task) {
    hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${task.id}/assign")
    hxTarget("#task-${task.id}")
    assign(users, currentUser, task.assignee)
}