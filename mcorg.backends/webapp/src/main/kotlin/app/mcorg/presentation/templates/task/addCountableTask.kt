package app.mcorg.presentation.templates.task

import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun addCountableTask(backLink: String) = subPageTemplate("Add countable task", backLink = backLink) {
    form {
        encType = FormEncType.applicationXWwwFormUrlEncoded
        method = FormMethod.post
        label {
            + "What needs to be counted?"
            input {
                id = "add-countable-task-name-input"
                type = InputType.text
                name = "taskName"
            }
        }
        label {
            + "How much do you need?"
            input {
                id = "add-countable-task-amount-input"
                type = InputType.number
                name = "amount"
                min = "0"
                max = "100000000"
            }
        }
        button {
            id = "add-countable-task-submit-button"
            type = ButtonType.submit
            + "Add task"
        }
    }
}