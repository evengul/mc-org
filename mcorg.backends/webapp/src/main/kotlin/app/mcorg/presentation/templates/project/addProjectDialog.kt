package app.mcorg.presentation.templates.project

import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.*

fun MAIN.addProjectDialog(worldId: Int, isTechnical: Boolean) {
    dialog {
        id = "add-project-dialog"
        h1 {
            + "Create a new project"
        }
        form {
            id = "add-project-form"
            method = FormMethod.post
            encType = FormEncType.applicationXWwwFormUrlEncoded
            hxPost("/app/worlds/$worldId/projects")
            hxTarget("#project-list")
            hxSwap("afterbegin")
            label {
                htmlFor = "project-add-name-input"
                + "Name of project"
            }
            input {
                id = "project-add-name-input"
                type = InputType.text
                required = true
                minLength = "2"
                maxLength = "200"
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
            span {
                classes = setOf("button-row")
                button {
                    type = ButtonType.button
                    classes = setOf("button-secondary")
                    onClick = "hideDialog('add-project-dialog')"
                    + "Cancel"
                }
                button {
                    id = "project-add-submit-button"
                    type = ButtonType.submit
                    + "Create"
                }
            }
        }
    }
}