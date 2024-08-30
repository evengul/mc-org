package app.mcorg.presentation.templates.task

import app.mcorg.presentation.templates.baseTemplate
import kotlinx.html.*

fun addDoableTask() = baseTemplate {
    nav {
        button {
            + "Back"
        }
        h1 {
            + "Add doable"
        }
    }
    main {
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
}