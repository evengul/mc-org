package app.mcorg.presentation.templates.task

import app.mcorg.domain.projects.Project
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.*

fun MAIN.addCountableDialog(project: Project) {
    dialog {
        id = "add-task-countable-dialog"
        form {
            id = "add-task-countable-form"
            encType = FormEncType.applicationXWwwFormUrlEncoded
            method = FormMethod.post
            hxPost("/app/worlds/${project.worldId}/projects/${project.id}/tasks/countable")
            hxTarget("#task-list")
            hxSwap("afterbegin")
            label {
                htmlFor = "add-countable-task-name-input"
                + "What needs to be counted?"
            }
            input {
                id = "add-countable-task-name-input"
                type = InputType.text
                name = "taskName"
                required = true
                minLength = "2"
                maxLength = "200"
            }
            label {
                htmlFor = "add-countable-task-amount-input"
                + "How much do you need?"
            }
            input {
                id = "add-countable-task-amount-input"
                type = InputType.number
                name = "amount"
                required = true
                min = "0"
                max = "100000000"
            }
            span {
                classes = setOf("button-row")
                button {
                    type = ButtonType.button
                    classes = setOf("button-secondary")
                    onClick = "hideDialog('add-task-countable-dialog')"
                    + "Cancel"
                }
                button {
                    id = "add-countable-task-submit-button"
                    type = ButtonType.submit
                    + "Add task"
                }
            }
        }
    }
}