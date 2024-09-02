package app.mcorg.presentation.templates.world

import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun addWorld(backLink: String): String = subPageTemplate("Create world", backLink = backLink) {
    form {
        method = FormMethod.post
        encType = FormEncType.applicationXWwwFormUrlEncoded
        label {
            input {
                id = "add-world-name-input"
                name = "worldName"
                type = InputType.text
                required = true
                minLength = "3"
                maxLength = "100"
            }
            + "Name of your world"
        }
        button {
            id = "add-world-submit-button"
            type = ButtonType.submit
            + "Create"
        }
    }
}