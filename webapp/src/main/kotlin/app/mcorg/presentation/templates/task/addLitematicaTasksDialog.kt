package app.mcorg.presentation.templates.task

import app.mcorg.domain.Project
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.*

fun MAIN.addLitematicaTasksDialog(project: Project) {
    dialog {
        id = "add-task-litematica-dialog"
        form {
            id = "add-task-litematica-form"
            encType = FormEncType.multipartFormData
            method = FormMethod.post
            hxPost("/app/worlds/${project.worldId}/projects/${project.id}/tasks/litematica")
            hxTarget("#task-list")
            hxSwap("afterbegin")
            label {
                htmlFor = "tasks-add-litematica-file-input"
                + "Select litematica material list"
            }
            input {
                id = "tasks-add-litematica-file-input"
                type = InputType.file
                name = "file"
            }
            span {
                classes = setOf("button-row")
                button {
                    type = ButtonType.button
                    classes = setOf("button-secondary")
                    onClick = "hideDialog('add-task-litematica-dialog')"
                    + "Cancel"
                }
                button {
                    type = ButtonType.submit
                    + "Upload"
                }
            }
        }
    }
}