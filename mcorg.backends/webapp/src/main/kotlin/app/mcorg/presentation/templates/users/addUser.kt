package app.mcorg.presentation.templates.users

import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun addUser(backLink: String): String = subPageTemplate("Add user", backLink = backLink) {
    form {
        id = "user-add"
        encType = FormEncType.applicationXWwwFormUrlEncoded
        method = FormMethod.post
        label {
            htmlFor = "add-user-username-input"
            + "Username"
        }
        input {
            id = "add-user-username-input"
            type = InputType.text
            name = "username"
            autoComplete = false
            required = true
        }
        p {
            + "Must be an exact match, and must have signed in to MC-ORG before"
        }
        button {
            id = "add-user-submit-button"
            type = ButtonType.submit
            + "Add user"
        }
    }
}