package app.mcorg.presentation.templates.task

import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun addDoableTask(backLink: String) = subPageTemplate("Add doable task", backLink = backLink) {
    form {
        encType = FormEncType.applicationXWwwFormUrlEncoded
        method = FormMethod.post
        label {
            htmlFor = "add-doable-task-name-input"
            + "What needs to be done?"
        }
        input {
            id = "add-doable-task-name-input"
            type = InputType.text
            name = "taskName"
        }
        button {
            id = "add-doable-task-submit-button"
            type = ButtonType.submit
            + "Add task"
        }
    }
}