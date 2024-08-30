package app.mcorg.presentation.templates.task

import app.mcorg.presentation.templates.baseTemplate
import kotlinx.html.*

fun addCountableTask() = baseTemplate {
    nav {
        button {
            + "Back"
        }
        h1 {
            + "Add countable task"
        }
    }
    main {
        form {
            label {
                + "What needs to be counted?"
                input {
                    type = InputType.text
                    name = "taskName"
                }
            }
            label {
                + "How much do you need?"
                input {
                    type = InputType.number
                    name = "amount"
                    min = "0"
                    max = "100000000"
                }
            }
            button {
                type = ButtonType.submit
                + "Add task"
            }
        }
    }
}