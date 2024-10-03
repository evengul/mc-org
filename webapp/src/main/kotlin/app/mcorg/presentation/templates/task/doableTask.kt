package app.mcorg.presentation.templates.task

import app.mcorg.domain.Project
import app.mcorg.domain.Task
import app.mcorg.domain.User
import app.mcorg.domain.isDone
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun createDoableTask(project: Project, task: Task, users: List<User>, currentUser: User) = createHTML().li {
    doableTask(task, users, currentUser, project)
}

fun LI.doableTask(task: Task, users: List<User>, currentUser: User, project: Project) {
    genericTask(project, task, users, currentUser)
    span {
        classes = setOf("task-doable-input")
        label {
            + "Complete: "
        }
        input {
            classes = setOf("task-doable-checkbox")
            id = "project-doable-task-${task.id}-change-input"
            if (task.isDone()) {
                hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${task.id}/incomplete")
            } else {
                hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/${task.id}/complete")
            }
            hxTarget("#task-${task.id}")
            hxSwap("outerHTML")
            type = InputType.checkBox
            checked = task.isDone()
        }
    }
}