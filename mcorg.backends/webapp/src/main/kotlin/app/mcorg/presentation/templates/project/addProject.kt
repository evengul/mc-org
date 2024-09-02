package app.mcorg.presentation.templates.project

import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun addProject(backLink: String, isTechnical: Boolean): String = subPageTemplate("Add project", backLink = backLink) {
    form {
        method = FormMethod.post
        encType = FormEncType.applicationXWwwFormUrlEncoded
        label {
            htmlFor = "project-add-name-input"
            + "Name of project"
        }
        input {
            id = "project-add-name-input"
            type = InputType.text
            required = true
            name = "projectName"
        }
        label {
            htmlFor = "project-add-dimension-input"
            + "Dimension"
        }
        select {
            id = "project-add-dimension-input"
            required = true
            name = "dimension"
            option {
                value = "OVERWORLD"
                + "Overworld"
            }
            option {
                value = "NETHER"
                + "Nether"
            }
            option {
                value = "THE_END"
                + "The End"
            }
        }
        label {
            htmlFor = "project-add-priority-input"
            + "Priority"
        }
        select {
            id = "project-add-priority-input"
            required = true
            name = "priority"
            option {
                value = "LOW"
                + "Low"
            }
            option {
                value = "MEDIUM"
                + "Medium"
            }
            option {
                value = "HIGH"
                + "High"
            }
        }
        if (isTechnical) {
            label {
                htmlFor = "project-add-requires-perimeter-input"
                + "Requires perimeter"
            }
            input {
                id = "project-add-requires-perimeter-input"
                type = InputType.checkBox
                name = "requiresPerimeter"
            }
        }
        button {
            id = "project-add-submit-button"
            type = ButtonType.submit
            + "Create"
        }
    }
}