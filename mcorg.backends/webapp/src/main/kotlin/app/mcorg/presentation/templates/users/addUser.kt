package app.mcorg.presentation.templates.users

import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun addUser(backLink: String): String = subPageTemplate("Add user", backLink = backLink) {
    form {
        encType = FormEncType.applicationXWwwFormUrlEncoded
        method = FormMethod.post
        label {
            input {
                type = InputType.text
                name = "username"
                autoComplete = false
            }
            + "Username: Must be an exact match, and must have signed in to MC-ORG before"
        }
        button {
            + "Add user"
        }
    }
}