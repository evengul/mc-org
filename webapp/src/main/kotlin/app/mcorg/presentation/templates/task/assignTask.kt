package app.mcorg.presentation.templates.task

import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.users.User
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templates.assign
import kotlinx.html.*

fun SELECT.assignTask(
    worldId: Int,
    projectId: Int,
    task: Task,
    users: List<User>,
    currentUser: User
) {
    hxPatch("/app/worlds/${worldId}/projects/${projectId}/tasks/${task.id}/assign")
    hxTarget("#task-${task.id}")
    assign(users, currentUser, task.assignee)
}