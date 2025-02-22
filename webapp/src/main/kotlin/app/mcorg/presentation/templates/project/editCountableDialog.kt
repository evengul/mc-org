package app.mcorg.presentation.templates.project

import app.mcorg.domain.projects.Project
import app.mcorg.presentation.hxPatch
import kotlinx.html.*

fun MAIN.editCountableDialog(project: Project) {
    dialog {
        id = "edit-task-dialog"
        h1 {
            + "Edit task"
        }
        form {
            id = "edit-countable-task-form"
            hxPatch("/app/worlds/${project.worldId}/projects/${project.id}/tasks/requirements")
            input {
                id = "edit-task-id-input"
                name = "id"
                type = InputType.text
            }
            label {
                htmlFor = "edit-task-done-input"
                + "Done"
            }
            input {
                name = "done"
                id = "edit-task-done-input"
                type = InputType.number
                required = true
                min = "0"
            }
            label {
                htmlFor = "edit-task-needed-input"
                + "Needed"
            }
            input {
                name = "needed"
                id = "edit-task-needed-input"
                type = InputType.number
                required = true
                min = "0"
                max = "200000000"
            }
            button {
                onClick = "hideDialog('edit-task-dialog')"
                classes = setOf("button-secondary")
                type = ButtonType.button
                + "Cancel"
            }
            button {
                type = ButtonType.submit
                + "Save"
            }
        }
    }
}