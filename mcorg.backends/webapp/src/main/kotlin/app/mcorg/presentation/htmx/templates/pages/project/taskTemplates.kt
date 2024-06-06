package app.mcorg.presentation.htmx.templates.pages.project

import kotlinx.html.button
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.stream.createHTML
import app.mcorg.domain.Task
import app.mcorg.presentation.htmx.hxPut
import app.mcorg.presentation.htmx.hxSwap

fun incompleteTaskButton(worldId: Int, teamId: Int, projectId: Int, taskId: Int): String {
    return createHTML().button {
        id = "task-$taskId-incomplete-button"
        hxPut("/worlds/$worldId/teams/$teamId/projects/$projectId/tasks/$taskId/incomplete")
        hxSwap("outerHTML")
        + "Not complete"
    }
}

fun completeTaskButton(worldId: Int, teamId: Int, projectId: Int, taskId: Int): String {
    return createHTML().button {
        id = "task-$taskId-complete-button"
        hxPut("/worlds/$worldId/teams/$teamId/projects/$projectId/tasks/$taskId/complete")
        hxSwap("outerHTML")
        + "Complete!"
    }
}

fun countableTaskListElement(worldId: Int, teamId: Int, projectId: Int, task: Task): String {
    return createHTML().li {
        id = "countable-task-${task.id}"
        + task.name
        updateCountableForm(worldId, teamId, projectId, task)
        deleteTask(worldId, teamId, projectId, task.id)
    }
}