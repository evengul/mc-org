package no.mcorg.presentation.htmx.templates.pages.project

import kotlinx.html.button
import kotlinx.html.li
import kotlinx.html.stream.createHTML
import no.mcorg.domain.Task
import no.mcorg.presentation.htmx.templates.hxPut
import no.mcorg.presentation.htmx.templates.hxSwap

fun incompleteTaskButton(worldId: Int, teamId: Int, projectId: Int, taskId: Int): String {
    return createHTML().button {
        hxPut("/worlds/$worldId/teams/$teamId/projects/$projectId/tasks/$taskId/incomplete")
        hxSwap("outerHTML")
        + "Not complete"
    }
}

fun completeTaskButton(worldId: Int, teamId: Int, projectId: Int, taskId: Int): String {
    return createHTML().button {
        hxPut("/worlds/$worldId/teams/$teamId/projects/$projectId/tasks/$taskId/complete")
        hxSwap("outerHTML")
        + "Complete!"
    }
}

fun countableTaskListElement(worldId: Int, teamId: Int, projectId: Int, task: Task): String {
    return createHTML().li {
        + task.name
        updateCountableForm(worldId, teamId, projectId, task)
        deleteTask(worldId, teamId, projectId, task.id)
    }
}