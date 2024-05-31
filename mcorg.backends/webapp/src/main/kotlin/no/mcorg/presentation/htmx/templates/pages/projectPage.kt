package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import no.mcorg.domain.Project
import no.mcorg.domain.Task
import no.mcorg.domain.isCountable
import no.mcorg.domain.isDone
import no.mcorg.presentation.htmx.templates.*

fun projectPage(project: Project): String {
    return page(title = project.name) {
        h2 {
            + "This is a project you have created"
        }
        button {
            type = ButtonType.button
            hxGet("/htmx/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}/tasks/add-countable")
            hxTarget("#add-task-container")
            + "Add countable/gathering task to project"
        }
        button {
            type = ButtonType.button
            hxGet("/htmx/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}/tasks/add-doable")
            hxTarget("#add-task-container")
            + "Add doable task to project"
        }
        div {
            id = "add-task-container"
        }
        ul {
            for (task in project.tasks.sortedByDescending { it.isDone() }) {
                li {
                    + task.name
                    if (task.isCountable()) {
                        updateCountableForm(project.worldId, project.teamId, project.id, task)
                    } else {
                        if (task.isDone()) {
                            button {
                                hxPut("/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}/tasks/${task.id}/incomplete")
                                hxSwap("outerHTML")
                                + "Undo completion"
                            }
                        } else {
                            button {
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
        encType = FormEncType.multipartFormData
        hxPut("/worlds/${worldId}/teams/${teamId}/projects/${projectId}/tasks/${task.id}/update-countable")
        hxTarget("closest li")
        hxSwap("outerHTML")
        label {
            htmlFor = "update-countable-task-done-input"
            + "How much is done?"
        }
        input {
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
            name = "needed"
            type = InputType.number
            required = true
            min = "1"
            max = "2000000000"
            value = task.needed.toString()
        }
        button {
            type = ButtonType.submit
            + "Update"
        }
    }
}

fun LI.deleteTask(worldId: Int, teamId: Int, projectId: Int, taskId: Int) {
    button {
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
                type = ButtonType.submit
                + "Add task"
            }
        }
}

fun addCountableTask(worldId: Int, teamId: Int, projectId: Int): String {
    return createHTML()
        .form {
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
                type = InputType.number
                name = "task-needed"
                required = true
                min = "1"
                max = "2000000000"
            }
            button {
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