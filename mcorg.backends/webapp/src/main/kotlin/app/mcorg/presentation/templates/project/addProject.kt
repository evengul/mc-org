package app.mcorg.presentation.templates.project

import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun addProject(backLink: String, isTechnical: Boolean): String = subPageTemplate("Add project", backLink = backLink) {
    form {
        method = FormMethod.post
        encType = FormEncType.applicationXWwwFormUrlEncoded
        label {
            + "Name of project"
            input {
                id = "project-add-name-input"
                type = InputType.text
                required = true
                name = "projectName"
            }
        }
        label {
            + "Dimension"
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
        }
        label {
            + "Priority"
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
        }
        if (isTechnical) {
            label {
                + "Requires perimeter"
                input {
                    id = "project-add-requires-perimeter-input"
                    type = InputType.checkBox
                    name = "requiresPerimeter"
                }
            }
        }
        button {
            id = "project-add-submit-button"
            type = ButtonType.submit
            + "Create"
        }
    }
}