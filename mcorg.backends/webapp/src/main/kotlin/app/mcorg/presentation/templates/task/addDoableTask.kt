package app.mcorg.presentation.templates.task

import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun addDoableTask(backLink: String) = subPageTemplate("Add doable task", backLink = backLink) {
    form {
        encType = FormEncType.applicationXWwwFormUrlEncoded
        method = FormMethod.post
        label {
            + "What needs to be done?"
            input {
                type = InputType.text
                name = "taskName"
            }
        }
        button {
            type = ButtonType.submit
            + "Add task"
        }
    }
}