package app.mcorg.presentation.htmx.templates.pages.project

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import app.mcorg.domain.Project
import app.mcorg.domain.Task
import app.mcorg.domain.isCountable
import app.mcorg.domain.isDone
import app.mcorg.presentation.htmx.templates.*
import app.mcorg.presentation.htmx.templates.pages.page

fun projectPage(project: Project): String {
    return page(title = project.name) {
        button(classes = OPEN_FORM_BUTTON) {
            id = "project-add-countable-task-show-form-button"
            type = ButtonType.button
            hxGet("/htmx/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}/tasks/add-countable")
            hxTarget("#add-task-container")
            + "Add countable/gathering task to project"
        }
        button(classes = OPEN_FORM_BUTTON) {
            id = "project-add-doable-task-show-form-button"
            type = ButtonType.button
            hxGet("/htmx/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}/tasks/add-doable")
            hxTarget("#add-task-container")
            + "Add doable task to project"
        }
        div {
            id = "add-task-container"
        }
        ul(classes = ENTITY_LIST) {
            id = "task-list"
            for (task in project.tasks.sortedByDescending { it.isDone() }) {
                li {
                    id = "task-${task.id}"
                    + task.name
                    if (task.isCountable()) {
                        updateCountableForm(project.worldId, project.teamId, project.id, task)
                    } else {
                        if (task.isDone()) {
                            button {
                                id = "complete-task-${task.id}-button"
                                hxPut("/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}/tasks/${task.id}/incomplete")
                                hxSwap("outerHTML")
                                + "Undo completion"
                            }
                        } else {
                            button {
                                id = "incomplete-task-${task.id}-button"
                                hxPut("/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}/tasks/${task.id}/complete")
                                hxSwap("outerHTML")
                                + "Complete!"
                            }
                        }
                    }
                    deleteTask(project.worldId, project.teamId, project.id, task.id)
                }
            }
        }
    }
}

fun LI.updateCountableForm(worldId: Int, teamId: Int, projectId: Int, task: Task) {
    form {
        id = "updatable-task-update-form"
        encType = FormEncType.multipartFormData
        hxPut("/worlds/${worldId}/teams/${teamId}/projects/${projectId}/tasks/${task.id}/update-countable")
        hxTarget("closest li")
        hxSwap("outerHTML")
        label {
            htmlFor = "update-countable-task-done-input"
            + "How much is done?"
        }
        input {
            id = "update-countable-task-done-input"
            name = "done"
            type = InputType.number
            required = true
            min = "0"
            max = task.needed.toString()
            value = task.done.toString()
        }
        label {
            htmlFor = "update-countable-task-needed-input"
            + "How much is needed?"
        }
        input {
            id = "update-countable-task-needed-input"
            name = "needed"
            type = InputType.number
            required = true
            min = "1"
            max = "2000000000"
            value = task.needed.toString()
        }
        button {
            id = "update-countable-task-submit"
            type = ButtonType.submit
            + "Update"
        }
    }
}

fun LI.deleteTask(worldId: Int, teamId: Int, projectId: Int, taskId: Int) {
    button {
        id = "delete-task-$taskId-button"
        type = ButtonType.button
        hxDelete("/worlds/$worldId/teams/$teamId/projects/$projectId/tasks/$taskId")
        hxTarget("closest li")
        hxSwap("outerHTML")
        + "Delete"
    }
}

fun addDoableTask(worldId: Int, teamId: Int, projectId: Int): String {
    return createHTML()
        .form {
            id = "add-doable-task-form"
            encType = FormEncType.multipartFormData
            method = FormMethod.post
            action = "/worlds/$worldId/teams/$teamId/projects/$projectId/tasks"
            label {
                htmlFor = "create-doable-task-name-input"
                + "What are you doing?"
            }
            input {
                id = "create-doable-task-name-input"
                name = "task-name"
                required = true
                minLength = "3"
                maxLength = "120"
            }
            label {
                htmlFor = "create-doable-task-priority-input"
                + "Priority of task"
            }
            priorities("create-doable-task-priority-input")
            button {
                id = "add-doable-task-submit"
                type = ButtonType.submit
                + "Add task"
            }
        }
}

fun addCountableTask(worldId: Int, teamId: Int, projectId: Int): String {
    return createHTML()
        .form {
            id = "add-countable-task-form"
            encType = FormEncType.multipartFormData
            method = FormMethod.post
            action = "/worlds/$worldId/teams/$teamId/projects/$projectId/tasks"
            label {
                htmlFor = "create-countable-task-name-input"
                + "What are you gathering?"
            }
            input {
                id = "create-countable-task-name-input"
                name = "task-name"
                required = true
                minLength = "3"
                maxLength = "120"
            }
            label {
                htmlFor = "create-countable-task-priority-input"
                + "Priority of task"
            }
            priorities("create-countable-task-priority-input")
            label {
                htmlFor = "create-countable-task-needed-input"
                + "Required to gather"
            }
            input {
                id = "create-countable-task-needed-input"
                type = InputType.number
                name = "task-needed"
                required = true
                min = "1"
                max = "2000000000"
            }
            button {
                id = "create-countable-task-submit"
                type = ButtonType.submit
                + "Add task"
            }
        }
}

private fun FORM.priorities(id: String) {
    return select {
        this.id = id
        required = true
        name = "task-priority"
        option {
            value = "NONE"
            + "None"
        }
        option {
            value = "LOW"
            + "Low"
        }
        option {
            value = "MEDIUM"
            + "Medium"
        }
        option {
            value = "HIGH"
            + "High"
        }
    }
}