package app.mcorg.presentation.templates.task

import app.mcorg.domain.Project
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.*

fun MAIN.addDoableTaskDialog(project: Project) {
    dialog {
        id = "add-task-doable-dialog"
        form {
            id = "add-task-doable-form"
            encType = FormEncType.applicationXWwwFormUrlEncoded
            method = FormMethod.post
            hxPost("/app/worlds/${project.worldId}/projects/${project.id}/tasks/doable")
            hxTarget("#task-list")
            hxSwap("afterbegin")
            label {
                htmlFor = "add-doable-task-name-input"
                + "What needs to be done?"
            }
            input {
                id = "add-doable-task-name-input"
                type = InputType.text
                name = "taskName"
                required = true
                minLength = "2"
                maxLength = "200"
            }
            span {
                classes = setOf("button-row")
                button {
                    type = ButtonType.button
                    classes = setOf("button-secondary")
                    onClick = "hideDialog('add-task-doable-dialog')"
                    + "Cancel"
                }
                button {
                    id = "add-doable-task-submit-button"
                    type = ButtonType.submit
                    + "Add task"
                }
            }
        }
    }
}