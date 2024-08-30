package app.mcorg.presentation.v2.templates.world

import app.mcorg.presentation.v2.templates.baseTemplate
import kotlinx.html.*

fun addWorld(): String = baseTemplate("MC-ORG - Create World") {
    nav {
        button {
            + "Back"
        }
        h1 {
            + "Create World"
        }
    }
    main {
        form {
            method = FormMethod.post
            encType = FormEncType.applicationXWwwFormUrlEncoded
            label {
                input {
                    name = "worldName"
                    type = InputType.text
                    required = true
                    minLength = "3"
                    maxLength = "100"
                }
                + "Name of your world"
            }
            button {
                type = ButtonType.submit
                + "Create"
            }
        }
    }
}