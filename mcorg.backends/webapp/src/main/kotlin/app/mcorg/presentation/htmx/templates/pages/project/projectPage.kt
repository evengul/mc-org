package app.mcorg.presentation.htmx.templates.pages.project

import app.mcorg.domain.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import app.mcorg.presentation.htmx.*
import app.mcorg.presentation.htmx.templates.*
import app.mcorg.presentation.htmx.templates.pages.page

fun projectPage(project: Project, tab: String): String {
    return page(title = project.name, id = "project-page", beforeMain = {
        nav {
            id = "project-tabs"
            ul {
                li(classes = if (tab == "doable-tasks") "selected" else "") {
                    h2 {
                        if (tab == "doable-tasks") {
                            + "Doable tasks"
                        } else {
                            a {
                                href = "?tab=doable-tasks"
                                + "Doable tasks"
                            }
                        }
                    }
                }
                li(classes = if (tab == "countable-tasks") "selected" else "") {
                    h2 {
                        if (tab == "countable-tasks") {
                            + "Countable tasks"
                        } else {
                            a {
                                href = "?tab=countable-tasks"
                                + "Countable tasks"
                            }
                        }
                    }
                }
            }
        }
    }) {
        script {
            src = "/static/response-targets.js"
            defer = true
        }
        if (tab == "doable-tasks") {
            form(classes = "add-task-form") {
                id = "add-doable-task-form"
                encType = FormEncType.multipartFormData
                method = FormMethod.post
                action = "/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}/tasks"
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
            ul(classes = "task-list") {
                for (task in project.doable().sortedBy { it.isDone() }) {
                    li {
                        id = "task-${task.id}"
                        + task.name
                        if (task.isDone()) {
                            button {
                                id = "complete-task-${task.id}-button"
                                hxPut("/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}/tasks/${task.id}/incomplete")
                                hxSwap("outerHTML")
                                + "Not complete"
                            }
                        } else {
                            button {
                                id = "incomplete-task-${task.id}-button"
                                hxPut("/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}/tasks/${task.id}/complete")
                                hxSwap("outerHTML")
                                + "Complete!"
                            }
                        }
                        deleteTask(project.worldId, project.teamId, project.id, task.id)
                    }
                }
            }
        } else if (tab == "countable-tasks") {
            form(classes = "add-task-form") {
                id = "add-countable-task-form"
                encType = FormEncType.multipartFormData
                method = FormMethod.post
                action = "/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}/tasks"
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
            form(classes = "add-task-form") {
                encType = FormEncType.multipartFormData
                method = FormMethod.post
                hxExtension("response-targets")
                hxPost("/worlds/${project.worldId}/teams/${project.teamId}/projects/${project.id}/tasks/material-list")
                hxTargetError("#litematica-upload-error")
                label {
                    htmlFor = "litematica-file-selector"
                    + "Select litematica material list"
                }
                input {
                    id = "litematica-file-selector"
                    name = "file"
                    type = InputType.file
                }
                button {
                    + "Upload litematica material list"
                }
                p(classes = "text-error") {
                    id = "litematica-upload-error"
                }
            }
            ul(classes = "task-list") {
                for (task in project.countable().sortedByDescending { it.needed }) {
                    li {
                        id = "task-${task.id}"
                        + task.name
                        updateCountableForm(project.worldId, project.teamId, project.id, task)
                        deleteTask(project.worldId, project.teamId, project.id, task.id)
                    }
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
            htmlFor = "update-countable-task-${task.id}-done-input"
            + "How much is done?"
        }
        input {
            id = "update-countable-task-${task.id}-done-input"
            name = "done"
            type = InputType.number
            required = true
            min = "0"
            max = task.needed.toString()
            value = task.done.toString()
        }
        label {
            htmlFor = "update-countable-task-${task.id}-needed-input"
            + "How much is needed?"
        }
        input {
            id = "update-countable-task-${task.id}-needed-input"
            name = "needed"
            type = InputType.number
            required = true
            min = "1"
            max = "2000000000"
            value = task.needed.toString()
        }
        button {
            id = "update-countable-task-${task.id}-submit"
            type = ButtonType.submit
            + "Update"
        }
    }
}

fun LI.deleteTask(worldId: Int, teamId: Int, projectId: Int, taskId: Int) {
    button(classes = "danger") {
        id = "delete-task-$taskId-button"
        type = ButtonType.button
        hxDelete("/worlds/$worldId/teams/$teamId/projects/$projectId/tasks/$taskId")
        hxTarget("closest li")
        hxSwap("outerHTML")
        hxConfirm("Are you sure you want to remove this task from this project?")
        + "Delete"
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