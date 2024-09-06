package app.mcorg.presentation.templates.world

import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun addWorld(backLink: String): String = subPageTemplate("Create world", backLink = backLink) {
    form {
        method = FormMethod.post
        encType = FormEncType.applicationXWwwFormUrlEncoded
        label {
            htmlFor = "add-world-name-input"
            + "Name of your world"
        }
        input {
            id = "add-world-name-input"
            name = "worldName"
            type = InputType.text
            required = true
            minLength = "3"
            maxLength = "100"
        }
        button {
            id = "add-world-submit-button"
            type = ButtonType.submit
            + "Create"
        }
    }
}